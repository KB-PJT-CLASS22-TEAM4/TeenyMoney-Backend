package com.teenyfin.teenymoney.domain.charge.service;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.charge.crypto.BillingKeyEncryptor;
import com.teenyfin.teenymoney.domain.charge.dto.response.ChargeMethodResponseDTO;
import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMethodMapper;
import com.teenyfin.teenymoney.domain.charge.toss.TossPaymentsClient;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossBillingKeyResponseDTO;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossCardBillingKeyRequestDTO;
import com.teenyfin.teenymoney.domain.charge.vo.ChargeMethodVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// @ExtendWith(SpringExtension.class): JUnit5한테 스프링 컨텍스트를 띄워달라고 연결해주는 어댑터.
@ExtendWith(SpringExtension.class)
// RootConfig에 등록된 빈들(DataSource, MyBatis 매퍼들)만 띄운다. LazyBeanInitializer가 꼭 필요한
// 이유: RootConfig가 domain 전체를 스캔하면서 TossPaymentsClient(RestTemplate 빈 필요 - 근데
// RestTemplateConfig는 config 패키지라 이 좁은 테스트 컨텍스트엔 없음)까지 즉시 만들려다 실패하는
// 걸 막아준다. ChargeMethodService/TossPaymentsClient는 여기서 직접 new/mock으로 조립한다
// (AllowanceServiceTest와 같은 패턴).
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
// 이 3개의 환경변수가 없으면 이 클래스 전체가 "건너뛰기" 처리된다.
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
// registerCard() 자체엔 @Transactional이 없지만(토스 호출 중 DB 커넥션을 오래 붙잡지 않으려고
// 일부러 뺐음 - ChargeMethodService 주석 참고), 테스트 메서드에 걸어두면 그 안에서 일어나는
// 매퍼 호출들이 이 테스트 트랜잭션에 합류해서 실행되고, 테스트가 끝나면 전부 자동 롤백된다.
// TransferService처럼 NOT_SUPPORTED로 트랜잭션을 일부러 끊는 로직이 여기엔 없어서 안전하다.
@Transactional
public class ChargeMethodServiceTest {

    @Autowired
    private ChargeMethodMapper chargeMethodMapper;

    @Autowired
    private MemberMapper memberMapper;

    // 진짜 토스 서버를 호출하면 안 되니 Mockito로 가짜 응답을 만들어 넣는다.
    // TossPaymentsClient는 외부 시스템 경계라 Mockito로 격리하는 게 맞다
    // ([[feedback-prefer-real-db-tests]] - "우리 DB/매퍼는 real-DB, 외부 시스템은 Mockito로").
    private TossPaymentsClient tossPaymentsClient;

    // BillingKeyEncryptor는 외부 의존이 없는 순수 암호화 로직이라 mock 안 하고 실제로 사용한다.
    // 테스트에서만 쓸 키를 즉석에서 무작위로 만들어서 생성자에 직접 넘김
    // (스프링의 @Value 없이도 그냥 new로 만들 수 있음 - 생성자 자체는 평범한 자바 생성자라서).
    private BillingKeyEncryptor billingKeyEncryptor;

    private ChargeMethodService chargeMethodService;

    private Long parentId;

    @BeforeEach
    void setUp() {
        tossPaymentsClient = mock(TossPaymentsClient.class);

        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        billingKeyEncryptor = new BillingKeyEncryptor(Base64.getEncoder().encodeToString(keyBytes));

        chargeMethodService = new ChargeMethodService(
                chargeMethodMapper, memberMapper, tossPaymentsClient, billingKeyEncryptor);

        parentId = insertMember("PARENT");
        System.out.println("[SETUP] parentId=" + parentId);
    }

    @Test
    void registerCardSavesEncryptedBillingKeyAndReturnsCardInfo() {
        // given: 토스가 이렇게 응답한다고 가정 (실제 토스 응답 구조 그대로 - card 중첩 객체 안에
        // number/issuerCode가 있음. 실제 서비스 테스트하면서 확인한 진짜 응답 형태를 그대로 따라감)
        stubTossResponse("real-billing-key-abc123", "94364662****800*", "11");
        MemberPrincipal principal = new MemberPrincipal(parentId, "PARENT");

        // when
        ChargeMethodResponseDTO response = chargeMethodService.registerCard(
                principal, "9436466261768004", "27", "12", "990101", "12");

        System.out.println("[RESULT] id=" + response.getId() + ", cardCompany=" + response.getCardCompany()
                + ", maskedCardNumber=" + response.getMaskedCardNumber() + ", primary=" + response.isPrimary());

        // then: 응답 DTO 확인 - issuerCode "11"은 CardIssuerCode 표에서 "국민"으로 변환돼야 함
        assertNotNull(response.getId());
        assertEquals("CARD", response.getType());
        assertEquals("국민", response.getCardCompany());
        assertEquals("94364662****800*", response.getMaskedCardNumber());
        assertFalse(response.isPrimary());
        assertEquals("ACTIVE", response.getStatus());

        // then: DB에 실제로 저장됐는지, 그리고 billing_key가 평문이 아니라 암호화된 값으로
        // 저장됐는지 확인 (암호화 안 됐으면 저장된 값이 "real-billing-key-abc123"과 같을 것임)
        List<ChargeMethodVO> saved = chargeMethodMapper.selectByParentId(parentId);
        assertEquals(1, saved.size());

        String storedBillingKey = saved.get(0).getBillingKey();
        System.out.println("[DB] 저장된 billing_key(암호화됨)=" + storedBillingKey);
        assertNotEquals("real-billing-key-abc123", storedBillingKey);

        // 저장된 값을 복호화하면 원래 값이 그대로 나와야 함 (암호화가 왕복 가능한지 검증)
        String decrypted = billingKeyEncryptor.decrypt(storedBillingKey);
        System.out.println("[DB] 복호화한 값=" + decrypted);
        assertEquals("real-billing-key-abc123", decrypted);
    }

    @Test
    void registerCardGeneratesCustomerKeyWhenMemberHasNone() {
        // given: 방금 만든 회원은 customerKey가 아직 null인 상태
        assertNull(memberMapper.selectById(parentId).getCustomerKey());
        stubTossResponse("billing-key-1", "94364662****800*", "11");
        MemberPrincipal principal = new MemberPrincipal(parentId, "PARENT");

        // when
        chargeMethodService.registerCard(principal, "9436466261768004", "27", "12", "990101", "12");

        // then: customerKey가 새로 생성되어 저장됐어야 함
        String customerKey = memberMapper.selectById(parentId).getCustomerKey();
        System.out.println("[RESULT] 새로 생성된 customerKey=" + customerKey);
        assertNotNull(customerKey);
    }

    @Test
    void registerCardReusesExistingCustomerKey() {
        // given: 이미 customerKey가 있는 회원 (예: 예전에 카드 한 장 등록해둔 상태를 흉내)
        String existingCustomerKey = "already-existing-customer-key";
        memberMapper.updateCustomerKeyIfAbsent(parentId, existingCustomerKey);
        stubTossResponse("billing-key-2", "55501234****456*", "61");
        MemberPrincipal principal = new MemberPrincipal(parentId, "PARENT");

        // when
        chargeMethodService.registerCard(principal, "5550123412345678", "28", "01", "010101", "34");

        // then: customerKey가 덮어써지지 않고 그대로여야 함
        String customerKeyAfter = memberMapper.selectById(parentId).getCustomerKey();
        System.out.println("[RESULT] 등록 후 customerKey=" + customerKeyAfter + " (기존 값과 같아야 함)");
        assertEquals(existingCustomerKey, customerKeyAfter);
    }

    @Test
    void registerCardDoesNotSaveWhenTossCallFails() {
        // given: 토스 호출 자체가 실패하는 상황을 흉내 (카드번호가 잘못된 경우 등)
        when(tossPaymentsClient.issueCardBillingKey(any(TossCardBillingKeyRequestDTO.class)))
                .thenThrow(new BusinessException(com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode.TOSS_BILLING_KEY_ISSUE_FAILED));
        MemberPrincipal principal = new MemberPrincipal(parentId, "PARENT");

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeMethodService.registerCard(
                        principal, "0000000000000000", "27", "12", "990101", "12"));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode.TOSS_BILLING_KEY_ISSUE_FAILED,
                exception.getErrorCode());

        // then: 토스 호출이 실패했으니 DB엔 아무것도 저장되면 안 됨
        List<ChargeMethodVO> saved = chargeMethodMapper.selectByParentId(parentId);
        System.out.println("[DB] 저장된 결제수단 개수=" + saved.size() + " (0이어야 함)");
        assertEquals(0, saved.size());
    }

    // ---------- 헬퍼 ----------

    // TossPaymentsClient.issueCardBillingKey(...)가 이렇게 응답한다고 가짜로 정해두는 헬퍼.
    // 실제 토스 응답 구조(card 중첩 객체 안에 number/issuerCode)를 그대로 흉내낸다.
    private void stubTossResponse(String billingKey, String maskedNumber, String issuerCode) {
        TossBillingKeyResponseDTO.Card card = new TossBillingKeyResponseDTO.Card();
        card.setNumber(maskedNumber);
        card.setIssuerCode(issuerCode);

        TossBillingKeyResponseDTO response = new TossBillingKeyResponseDTO();
        response.setBillingKey(billingKey);
        response.setCard(card);

        when(tossPaymentsClient.issueCardBillingKey(any(TossCardBillingKeyRequestDTO.class)))
                .thenReturn(response);
    }

    private Long insertMember(String role) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        int phoneSuffix = Math.floorMod(unique.hashCode(), 100_000_000);

        MemberVO member = new MemberVO();
        member.setRole(role);
        member.setName("결제수단테스트");
        member.setBirthDate(LocalDate.of(1990, 1, 1));
        member.setPhoneNumber(String.format("010%08d", phoneSuffix));
        member.setEmail("charge-method-" + unique + "@test.local");
        member.setPassword("$2a$10$test-only-hash");
        memberMapper.insert(member);
        return member.getId();
    }
}