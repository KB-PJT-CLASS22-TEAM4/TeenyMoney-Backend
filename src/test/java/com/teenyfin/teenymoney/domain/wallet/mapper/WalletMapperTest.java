package com.teenyfin.teenymoney.domain.wallet.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


// MemberMapperTest와 완전히 같은 뼈대. 아래 4개 어노테이션이 "진짜 DB에 붙는 테스트"를 만들어준다.
@ExtendWith(SpringExtension.class)                                     // JUnit이 Spring 빈 주입을 쓸 수 있게 함
@ContextConfiguration(classes = RootConfig.class)                      // 진짜 DataSource/MyBatis 설정을 통째로 로드
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")        // DB_URL 환경변수 없으면 이 클래스 전체를 건너뜀
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional                                                          // 각 테스트가 끝나면 자동 롤백 (실제 DB에 흔적 안 남음)
public class WalletMapperTest {
    // mock이 아니라 진짜로 SQL을 실행하는 WalletMapper 구현체가 주입된다.
    @Autowired
    private WalletMapper walletMapper;

    // RootConfig는 JdbcTemplate 빈을 따로 등록 안 해서(프로덕션 코드가 안 쓰니까),
    // 여기서만 필요한 용도로 DataSource로부터 직접 만든다.
    // "매퍼를 거치지 않고 컬럼을 직접 읽어서 검증"할 때 쓸 도구.
    private JdbcTemplate jdbcTemplate;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void selectWalletForUpdateAndUpdateBalanceRoundTripThroughRealColumn() {
        // given: 이 테스트 전용 지갑을 하나 새로 만든다.
        // WalletMapper엔 "지갑을 새로 만드는" 메서드가 아직 없어서(다음 태스크에서 만들 예정),
        // 여기선 jdbcTemplate으로 직접 INSERT한다. member_id=1은 시드 데이터의 부모 계정 id.
        jdbcTemplate.update(
                "INSERT INTO T_WLT_BASE_M (member_id, balance, type) VALUES (?, ?, ?)",
                1L, 50000L, "MEMBER");
        // MySQL의 LAST_INSERT_ID()로, 방금 INSERT된 행의 auto_increment id를 가져온다.
        Long walletId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        System.out.println("[SETUP] 테스트용 지갑 생성: id=" + walletId + ", balance=50000");

        // when: 방금 만든 지갑을 selectWalletForUpdate로 조회
        WalletVO wallet = walletMapper.selectWalletForUpdate(walletId);
        System.out.println("[SELECT] selectWalletForUpdate(" + walletId + ") -> balance=" + wallet.getBalance());

        // then: 방금 넣은 값 그대로 조회되는지 확인
        assertNotNull(wallet);
        assertEquals(50000L, wallet.getBalance());

        // when: updateBalance로 잔액을 80000으로 갱신
        walletMapper.updateBalance(walletId, 80000L);

        // then: 매퍼를 거치지 않고 컬럼을 "직접" 읽어서, 진짜로 DB에 반영됐는지 확인.
        Long rawBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM T_WLT_BASE_M WHERE id = ?", Long.class, walletId);
        System.out.println("[UPDATE] updateBalance(" + walletId + ", 80000) 이후 실제 컬럼 값 = " + rawBalance);

        assertEquals(80000L, rawBalance);
    }

    @Test
    void insertWalletHistoryWithChargeRefTypeFillsOnlyChargeIdColumn() {
        // given: 테스트 전용 지갑 하나 생성
        jdbcTemplate.update(
                "INSERT INTO T_WLT_BASE_M (member_id, balance, type) VALUES (?, ?, ?)",
                1L, 50000L, "MEMBER");
        Long walletId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        System.out.println("[SETUP] 테스트용 지갑 생성: id=" + walletId);

        // given: charge_id는 T_WLT_CHARGE_L을 가리키는 진짜 FK라서, 존재하지 않는 값을 넣으면
        // FK 제약 위반으로 막힌다(방금 겪은 에러). 그래서 T_WLT_CHARGE_L에 진짜 행을 먼저 만든다.
        // payment_method_id=1은 시드 데이터에 있는 부모의 카드(T_PAY_METHOD_M.id=1).
        jdbcTemplate.update(
                "INSERT INTO T_WLT_CHARGE_L (wallet_id, payment_method_id, idempotency_key, amount, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                walletId, 1L, java.util.UUID.randomUUID().toString(), 3000L, "SUCCESS");
        Long chargeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        System.out.println("[SETUP] 테스트용 충전 시도 생성: id=" + chargeId);

        // when: refType=CHARGE로 원장 기입 실행. 이번엔 진짜 존재하는 chargeId를 쓴다.
        walletMapper.insertWalletHistory(walletId, "CREDIT", 3000L, 53000L, "CHARGE", chargeId, null);

        // then: payment_id/transfer_id는 NULL, charge_id에만 방금 만든 진짜 id가 들어갔는지 확인
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT payment_id, transfer_id, charge_id, direction, amount, balance_after "
                        + "FROM T_WLT_HIST_H WHERE wallet_id = ?", walletId);

        System.out.println("[LEDGER] payment_id=" + row.get("payment_id")
                + ", transfer_id=" + row.get("transfer_id")
                + ", charge_id=" + row.get("charge_id")
                + ", direction=" + row.get("direction")
                + ", amount=" + row.get("amount")
                + ", balance_after=" + row.get("balance_after"));

        assertNull(row.get("payment_id"));
        assertNull(row.get("transfer_id"));
        assertEquals(chargeId, ((Number) row.get("charge_id")).longValue());
        assertEquals("CREDIT", row.get("direction"));
        assertEquals(53000L, ((Number) row.get("balance_after")).longValue());
    }
}
