package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionPeriod;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionSortOrder;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionTypeFilter;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletTransactionResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;


import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class WalletService {

    // "내 지갑 보기" 화면에 보여줄 최근 거래 개수 고정값. 매직넘버 대신 이름 붙여서 관리.
    private static final int RECENT_TRANSACTION_LIMIT = 3;

    private final WalletMapper walletMapper;
    private final Clock clock;
    private final FamilyAccessService familyAccessService;

    public WalletService(WalletMapper walletMapper, Clock clock, FamilyAccessService familyAccessService) {

        this.walletMapper = walletMapper;
        this.clock = clock;
        this.familyAccessService = familyAccessService;
    }

    // 잔액 + 최근 거래내역 3건을 한 번에 반환. 화면(내 지갑 보기)이 이 둘을 항상 같이 쓰기 때문에
    // API도 하나로 합쳐서, 프론트가 한 번의 호출로 화면을 다 그릴 수 있게 한다.
    @Transactional(readOnly = true)
    public WalletDetailResponseDTO getMyWalletDetail(Long memberId) {
        // 1단계: memberId로 "이 회원의 MEMBER 타입 지갑"을 찾는다. 없으면 여기서 예외로 끝남.
        WalletVO wallet = findMemberWalletOrThrow(memberId);

        // 2단계: 그 지갑의 id로 최신 거래내역 3건을 조회한다.
        List<WalletTransactionVO> transactions =
                walletMapper.selectRecentTransactions(wallet.getId(), RECENT_TRANSACTION_LIMIT);

        // 3단계: 지갑 정보 + 거래내역을 하나의 응답 DTO로 합쳐서 반환한다.
        return WalletDetailResponseDTO.of(wallet, transactions);
    }

    // 거래내역 목록 조회. 화면의 종류/기간/정렬 버튼 3개 상태를 받아서,
    // "내 지갑"으로 범위를 먼저 확정한 뒤 그 지갑의 거래만 조회한다.

    @Transactional(readOnly = true)
    public List<WalletTransactionResponseDTO> getMyTransactions(Long memberId, TransactionTypeFilter type, TransactionPeriod period, TransactionSortOrder sort) {

        // 1단계: memberId로 "이 회원의 지갑"을 먼저 확정한다.
        // 여기서 남의 memberId를 넣을 수가 없다 (principal.memberId()는 로그인 토큰에서만 나옴).
        // 그래서 아래 selectTransactions는 항상 "내 지갑"의 id로만 조회되고, 남의 거래내역을
        // 볼 방법이 구조적으로 없다.
        WalletVO wallet = findMemberWalletOrThrow(memberId);

        // 2단계: period(enum)에게 "오늘 날짜를 줄 테니 시작일 계산해줘"라고 요청.
        // clock을 직접 안 쓰고 LocalDate.now(clock)로 감싸는 이유는 테스트 때문이다.
        // 테스트에서는 고정된 가짜 Clock을 넣어서 "오늘"을 원하는 날짜로 고정할 수 있다.
        LocalDate startDate = period.startDateFrom(LocalDate.now(clock));

        // 3단계: enum(TransactionTypeFilter) -> Mapper가 원하는 String으로 변환.
        // ALL이면 null(=SQL에서 direction 조건 자체를 뺀다), 아니면 "CREDIT"/"DEBIT" 문자열.
        String direction = (type == TransactionTypeFilter.ALL) ? null : type.name();

        // 4단계: Mapper 호출. sort.name()은 enum 상수 이름을 그대로 문자열로 바꿔준다.
        // (TransactionSortOrder.ASC.name() == "ASC")
        List<WalletTransactionVO> transactions =
                walletMapper.selectTransactions(wallet.getId(), direction, startDate, sort.name());

        // 5단계: VO 리스트 -> DTO 리스트로 변환해서 반환. (DB 형태를 API 응답 형태로 바꾸는 단계)
        return transactions.stream()
                .map(WalletTransactionResponseDTO::of)
                .toList();

    }

    // 지갑이 없으면 에러 처리.
    private WalletVO findMemberWalletOrThrow(Long memberId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        return wallet;
    }

    // 새 지갑을 만들고, 생성된 지갑의 id를 리턴한다.
    // 호출한 쪽(예: 나중의 AuthService.signup(), 예적금 가입 서비스)이 이 id를
    // 자기 테이블(T_DPT_ENROLL_M.wallet_id 등)에 저장하는 데 쓴다.
    @Transactional
    public Long createWallet(Long memberId, WalletType type) {
        // MEMBER 타입은 "회원당 지갑 1개"가 원칙이라, 이미 있으면 막는다.
        // DEPOSIT/SAVING은 가입 건마다 새로 생기는 지갑이라 이 검사를 건너뛴다
        if (type == WalletType.MEMBER && walletMapper.selectMemberWalletByMemberId(memberId) != null) {
            throw new BusinessException(WalletErrorCode.WALLET_ALREADY_EXISTS);
        }

        WalletVO wallet = new WalletVO();
        wallet.setMemberId(memberId);
        wallet.setBalance(0L); // 신규 지갑은 항상 0원부터 시작
        wallet.setType(type.name()); // enum -> DB에 저장할 문자열("MEMBER" 등)로 변환

        try {
            walletMapper.insertWallet(wallet);
        } catch (DuplicateKeyException exception) {
            // 위 if문 체크와 이 INSERT 사이의 아주 짧은 틈에, 다른 요청이 먼저 같은 회원의
            // MEMBER 지갑을 만들어버린 경우(동시 요청 레이스)를 여기서 잡는다.
            // DB의 유니크 제약(마이그레이션에서 추가 예정)이 두 번째 INSERT를 막아주고,
            // 그 신호가 DuplicateKeyException으로 여기까지 올라온다.
            // 지금은 DB에 그 유니크 제약이 아직 없어서 이 catch가 실제로 걸릴 일은 없지만,
            // 마이그레이션 추가하면 이 catch가 비로소 의미를 갖게 된다.
            throw new BusinessException(WalletErrorCode.WALLET_ALREADY_EXISTS);
        }
        return wallet.getId();

    }

    @Transactional(readOnly = true)
    public WalletDetailResponseDTO getChildWalletDetail(MemberPrincipal principal, Long childId) {
        familyAccessService.requireChildAccess(principal, childId);
        return getMyWalletDetail(childId);
    }


    @Transactional(readOnly = true)
    public List<WalletTransactionResponseDTO> getChildTransactions(
            MemberPrincipal principal, Long childId,
            TransactionTypeFilter type, TransactionPeriod period, TransactionSortOrder sort) {
        familyAccessService.requireChildAccess(principal, childId);
        return getMyTransactions(childId, type, period, sort);
    }





}
