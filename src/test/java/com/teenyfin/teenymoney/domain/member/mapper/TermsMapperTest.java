package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.member.vo.AgreementVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 약관 조회 쿼리 검증. V001 마이그레이션이 넣어둔 SERVICE_TERMS / PRIVACY 행을 그대로 읽는다.
 *
 * 읽기 전용이라 정리(@AfterEach)가 없다. LazyBeanInitializer는 NotificationMapperTest와
 * 같은 이유로 붙는다 - RootConfig 전체 스캔 중 RestTemplate이 필요한 빈에서 터지는 걸 막는다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
public class TermsMapperTest {

    @Autowired
    private MemberMapper memberMapper;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Test
    void 유효_약관_목록에_서비스약관과_개인정보동의가_포함된다() {
        List<AgreementVO> agreements = memberMapper.selectEffectiveAgreements(NOW);

        // 개수는 단정하지 않는다. 약관이 추가되면 깨지는 테스트가 되기 때문.
        List<String> codes = agreements.stream().map(AgreementVO::getCode).toList();
        assertTrue(codes.contains("SERVICE_TERMS"), "SERVICE_TERMS가 목록에 없다: " + codes);
        assertTrue(codes.contains("PRIVACY"), "PRIVACY가 목록에 없다: " + codes);
    }

    @Test
    void 목록_조회는_전문을_내려주지_않는다() {
        List<AgreementVO> agreements = memberMapper.selectEffectiveAgreements(NOW);

        assertFalse(agreements.isEmpty());
        for (AgreementVO agreement : agreements) {
            assertNull(agreement.getContent(),
                    agreement.getCode() + "의 content가 목록에 실렸다");
            assertNotNull(agreement.getTitle());
            assertFalse(agreement.getTitle().isBlank());
        }
    }

    @Test
    void 코드로_조회하면_전문이_내려온다() {
        AgreementVO agreement = memberMapper.selectEffectiveAgreementByCode("SERVICE_TERMS", NOW);

        assertNotNull(agreement);
        assertEquals("SERVICE_TERMS", agreement.getCode());
        assertEquals("1.0", agreement.getVersion());
        assertNotNull(agreement.getContent());
        assertFalse(agreement.getContent().isBlank());
    }

    @Test
    void 없는_코드는_null을_반환한다() {
        assertNull(memberMapper.selectEffectiveAgreementByCode("NO_SUCH_CODE", NOW));
    }

    @Test
    void 적용_시작_전_시각으로_조회하면_비어있다() {
        // V001 약관의 effective_at은 2026-08-04. 그보다 앞선 시각이면 아직 유효하지 않다.
        LocalDateTime beforeEffective = LocalDateTime.of(2020, 1, 1, 0, 0);

        assertTrue(memberMapper.selectEffectiveAgreements(beforeEffective).isEmpty());
        assertNull(memberMapper.selectEffectiveAgreementByCode("SERVICE_TERMS", beforeEffective));
    }
}
