package com.teenyfin.teenymoney.domain.wallet.service;


import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional
public class WalletServiceCreateWalletTest {

    //RootConfig가 만들어주는 "진짜" WalletMapper (실제 DB에 SQL을 날림)
    @Autowired
    private WalletMapper walletMapper;

    // 테스트 전용 회원을 직접 INSERT하기 위한 JdbcTemplate.
    // (RootConfig엔 JdbcTemplate 빈이 따로 없어서, WalletMapperTest처럼 DataSource로부터 직접 만든다)
    private JdbcTemplate jdbcTemplate;

    // WalletService는 @Service라서 이 root-only 컨텍스트엔 빈으로 없다.
    // 그래서 Spring한테 주입해달라고 못하고, 방금 받은 진짜 walletMapper를 직접 new로 꽂아서 만든다.
    private WalletService walletService;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        // 이 테스트는 createWallet()만 검증하고 자녀 조회 기능은 안 다루므로,
        // FamilyAccessService는 실제 구현 대신 mock으로 채워서 생성자만 맞춘다.
        walletService = new WalletService(walletMapper, Clock.systemDefaultZone(), mock(FamilyAccessService.class));
    }

    // 테스트 전용 회원을 하나 새로 만들고 그 id를 리턴한다.
    // phone_number/email은 UNIQUE 제약이 있어서, 매번 겹치지 않게 UUID로 랜덤하게 만든다.
    private Long insertMemberWithoutWallet() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0,8);
        jdbcTemplate.update(
                "INSERT INTO T_MBR_INFO_M (role, name, birth_date, phone_number, email, password)" +
                        "VALUES (?,?,?,?,?,?)",
                "PARENT", "테스트용회원", java.sql.Date.valueOf("1990-01-01"),
                "010" + uniqueSuffix, "test-" + uniqueSuffix + "@naver.com", "dummy-hash");
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void createWalletInsertsNewMemberWalletWithZeroBalance() {
        // given:지갑이 아직 없는 새 회원 만들기
        Long memberId = insertMemberWithoutWallet();
        System.out.println("[SETUP] 테스트용 회원 생성: id=" + memberId);

        //when : 이 회원에게 MEMBER 타입 지갑을 생성
        Long walletId = walletService.createWallet(memberId, WalletType.MEMBER);
        System.out.println("[CREATE] 생성된 지갑 id=" + walletId);

        //then : 실제로 DB에 0원 잔액, MEMBER 타입으로 생겼는지 매퍼로 다시 조회해서 확인
        WalletVO wallet = walletMapper.selectWalletForUpdate(walletId);
        System.out.println("[VERIFY] memberId=" + wallet.getMemberId()
                + ", balance=" + wallet.getBalance() + ", type=" + wallet.getType());

        assertNotNull(wallet);
        assertEquals(memberId, wallet.getMemberId());
        assertEquals(0L, wallet.getBalance());
        assertEquals("MEMBER", wallet.getType());

    }

    @Test
    void createWalletThrowsWalletAlreadyExistsWhenMemberAlreadyHasMemberWallet() {
        //given: seed data에 이미 member_id=1이 있음
        System.out.println("[GIVEN] member_id=1은 seed data에 이미 MEMBER 지갑이 존재함");

        //when & then: 같은 회원에게 또 MEMBER 지갑을 만들려고 하면 막혀야 함
        BusinessException exception = assertThrows(BusinessException.class, () -> walletService.createWallet(1L, WalletType.MEMBER));
        assertEquals(WalletErrorCode.WALLET_ALREADY_EXISTS, exception.getErrorCode());
        System.out.println("[EXCEPTION] " + exception.getErrorCode() + " - " + exception.getMessage());

    }



}
