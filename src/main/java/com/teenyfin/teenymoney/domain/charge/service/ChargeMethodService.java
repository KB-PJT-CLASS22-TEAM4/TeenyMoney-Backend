package com.teenyfin.teenymoney.domain.charge.service;

import com.teenyfin.teenymoney.domain.charge.crypto.BillingKeyEncryptor;
import com.teenyfin.teenymoney.domain.charge.dto.response.ChargeMethodResponseDTO;
import com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode;
import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMethodMapper;
import com.teenyfin.teenymoney.domain.charge.toss.TossPaymentsClient;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossBillingKeyResponseDTO;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossCardBillingKeyRequestDTO;
import com.teenyfin.teenymoney.domain.charge.vo.ChargeMethodVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.teenyfin.teenymoney.domain.charge.toss.CardIssuerCode;

import java.util.List;
import java.util.UUID;
@Slf4j
@Service
public class ChargeMethodService {
    private final ChargeMethodMapper chargeMethodMapper;
    private final MemberMapper memberMapper;
    private final TossPaymentsClient tossPaymentsClient;
    private final BillingKeyEncryptor billingKeyEncryptor;

    public ChargeMethodService(ChargeMethodMapper chargeMethodMapper, MemberMapper memberMapper, TossPaymentsClient tossPaymentsClient, BillingKeyEncryptor billingKeyEncryptor) {
        this.chargeMethodMapper = chargeMethodMapper;
        this.memberMapper = memberMapper;
        this.tossPaymentsClient = tossPaymentsClient;
        this.billingKeyEncryptor = billingKeyEncryptor;
    }

    // 카드 등록. @Transactional을 일부러 안 붙임 - 이 메서드 중간에 토스 서버 호출(외부
    // 네트워크 I/O)이 껴있는데, 트랜잭션을 걸면 그 응답을 기다리는 동안 DB 커넥션을
    // 계속 붙잡고 있게 됨. 트래픽 몰릴 때 커넥션 풀이 고갈되는 원인이 될 수 있어서
    // (resolveCustomerKey의 updateCustomerKey, insert 각각은 단일 SQL문이라 원래도 개별적으로
    // 원자적임 - 굳이 하나로 묶을 필요가 없음).

    public ChargeMethodResponseDTO registerCard(
            MemberPrincipal principal,
            String cardNumber,
            String cardExpirationYear,
            String cardExpirationMonth,
            String customerIdentityNumber,
            String cardPassword) {

        Long parentId = principal.memberId();

        // 1단계: 이 부모의 토스 customerKey를 가져온다 (없으면 여기서 새로 만들어 저장).
        String customerKey = resolveCustomerKey(parentId);

        // 2단계: 토스한테 보낼 요청 객체를 만들어서 실제로 빌링키 발급을 요청한다.
        TossCardBillingKeyRequestDTO tossRequest = new TossCardBillingKeyRequestDTO(
                customerKey, cardNumber, cardExpirationYear,
                cardExpirationMonth, customerIdentityNumber, cardPassword);
        TossBillingKeyResponseDTO tossResponse = tossPaymentsClient.issueCardBillingKey(tossRequest);


        // 3단계: 발급받은 빌링키는 암호화해서, 나머지 정보(카드사/마스킹번호)는 그대로 VO에 채운다.
        ChargeMethodVO chargeMethod = new ChargeMethodVO();
        chargeMethod.setParentId(parentId);
        chargeMethod.setBillingKey(billingKeyEncryptor.encrypt(tossResponse.getBillingKey()));
        chargeMethod.setType("CARD");
        chargeMethod.setCardCompany(CardIssuerCode.toKoreanName(tossResponse.getCard().getIssuerCode()));
        chargeMethod.setMaskedCardNumber(tossResponse.getCard().getNumber());

        // 4단계: DB에 저장. insert 직후 chargeMethod.getId()에 생성된 PK가 채워짐
        try{
            chargeMethodMapper.insert(chargeMethod);
        } catch (RuntimeException exception) {
            // catch 타입은 RuntimeException 그대로 유지 - insert() 실패 시 스프링이 던지는 건
            // DataAccessException이지 BusinessException이 아니라서, 여기서 BusinessException으로
            // 좁히면 이 블록 자체가 절대 실행이 안 됨 (고아 빌링키 정리가 무력화됨).

            try {
                // 토스는 이미 빌링키 발급을 끝냈는데(자기네 시스템엔 이미 저장됨), 우리 DB
                // 저장이 여기서 실패하면 "우리는 모르는데 토스만 아는" 고아 빌링키가 생김.
                // 보상 동작으로 방금 발급받은 빌링키를 바로 삭제해서 정리를 시도한다.
                // 이 정리 자체가 또 실패해도, 원래 실패 원인(exception)을 감추지 않고 그대로 던진다 -
                // 사용자에게 보여줄 에러는 "등록 실패"가 맞고, 정리 실패는 로그로만 남겨서
                // 나중에 운영자가 토스 대시보드에서 수동으로 확인하게 함.
                tossPaymentsClient.deleteBillingKey(tossResponse.getBillingKey());
            } catch (RuntimeException cleanupException) {
                log.warn("고아 빌링키 정리 실패 - billingKey={}", tossResponse.getBillingKey(), cleanupException);
            }
            // 다만 던질 때는 우리 컨벤션대로 BusinessException + 전용 ErrorCode로 감싸서 던짐.
            // 원본 예외(exception)는 로그에 남겨서 실제 원인(DB 에러 종류 등)을 잃지 않게 함
            log.error("결제 수단 DB 저장 실패", exception);
            throw new BusinessException(ChargeErrorCode.CHARGE_METHOD_SAVE_FAILED);
        }


        return ChargeMethodResponseDTO.of(chargeMethod);


    }

    // 결제 수단 목록 조회
    public List<ChargeMethodResponseDTO> listChargeMethods(MemberPrincipal principal) {
        List<ChargeMethodVO> chargeMethods = chargeMethodMapper.selectByParentId(principal.memberId());

        // Stream.map: 리스트 안의 ChargeMethodVO 각각을 ChargeMethodResponseDTO로 하나씩 변환해서
        // 새 리스트를 만듦. for문으로 하나씩 돌면서 새 리스트에 담는 것과 결과는 같음, 더 짧게 쓴 것.
        return chargeMethods.stream()
                .map(ChargeMethodResponseDTO::of)
                .toList();
    }


    // 주거래 수단 지정. clearPrimaryByParentId + setPrimary 두 번의 UPDATE가 한 트랜잭션으로 묶음
    @Transactional
    public void setPrimary(MemberPrincipal principal, Long id) {
        // 소유권 확인: 이 id가 실제로 이 부모의 결제수단이 맞는지 먼저 검증.
        ChargeMethodVO chargeMethod = findOwnedChargeMethodOrThrow(principal.memberId(), id);

        chargeMethodMapper.clearPrimaryByParentId(principal.memberId());
        chargeMethodMapper.setPrimary(chargeMethod.getId());
    }


    // 결제수단 삭제. @Transactional 없음 - registerCard()랑 같은 이유로, 토스 삭제 API
    // 호출(외부 I/O)이 끝난 뒤에 updateStatus() 한 줄만 실행되는 구조라 트랜잭션으로
    // 묶을 이유가 없음(단일 SQL문은 원래 원자적).

    public void deleteChargeMethod(MemberPrincipal principal, Long id) {
        ChargeMethodVO chargeMethod = findOwnedChargeMethodOrThrow(principal.memberId(), id);

        //DB엔 암호화된 값이 저장돼있으니, 토스한테 보내려면 먼저 원래 값으로 복호화
        String billingKey = billingKeyEncryptor.decrypt(chargeMethod.getBillingKey());
        tossPaymentsClient.deleteBillingKey(billingKey);

        chargeMethodMapper.updateStatus(chargeMethod.getId(), "INACTIVE");
    }

    // customerKey 조회, 없으면 새로 만들어서 저장까지 하는 헬퍼.
    // registerCard()에서만 쓰지만, WalletLedgerService의 validateAmount처럼 별도 메서드로
    // 뽑아두면 registerCard() 본문이 "무엇을 하는지" 한눈에 더 잘 보임.
    private String resolveCustomerKey(Long parentId) {
        MemberVO member = memberMapper.selectById(parentId);
        String customerKey = member.getCustomerKey();

        if(customerKey != null) {
            return customerKey;
        }
        // 후보 값을 만들어서 "지금도 null일 때만" 조건부로 저장 시도
        String candidateKey = UUID.randomUUID().toString();
        int updateRows = memberMapper.updateCustomerKeyIfAbsent(parentId, candidateKey);

        if(updateRows == 1) {
            //이 요청이 먼저 도착해서 내가 만든 값이 채택된 경우
            return candidateKey;
        }
        // updatedRows == 0 : 그 사이 다른 요청(동시 등록 시도 등)이 이미 값을 채워놓은 경우.
        // 내가 만든 candidateKey는 버리고, 방금 채택된 진짜 값을 다시 읽어서 씀.
        MemberVO refreshed = memberMapper.selectById(parentId);
        return refreshed.getCustomerKey();
    }

    // id로 결제수단을 조회하고, 존재하는지 + 진짜 이 부모 소유가 맞는지까지 같이 검증하는 헬퍼.
    // setPrimary/deleteChargeMethod 둘 다 "남의 결제수단을 건드리면 안 된다"는 규칙이 있음
    private ChargeMethodVO findOwnedChargeMethodOrThrow(Long parentId, Long id) {
        ChargeMethodVO chargeMethod = chargeMethodMapper.selectById(id);

        // status까지 같이 확인 - 이미 삭제(INACTIVE)된 결제수단은 "존재하지 않는 것"과
        // 똑같이 취급함. 안 그러면 죽은 카드를 주거래로 지정하거나, 이미 삭제된 걸
        // 또 삭제 시도하는 이상한 상태가 가능해짐.
        if(chargeMethod == null || !"ACTIVE".equals(chargeMethod.getStatus())) {
            throw new BusinessException(ChargeErrorCode.CHARGE_METHOD_NOT_FOUND);
        }
        if(!chargeMethod.getParentId().equals(parentId)) {
            throw new BusinessException(ChargeErrorCode.CHARGE_METHOD_ACCESS_DENIED);
        }
        return chargeMethod;
    }


}
