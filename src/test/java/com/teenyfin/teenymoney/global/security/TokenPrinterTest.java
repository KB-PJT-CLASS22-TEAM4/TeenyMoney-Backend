package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로컬 개발용 도구. 검증이 아니라 값을 출력하는 것이 목적이다.
 *
 * 로그인 API(하위3)가 아직 없어 토큰을 정상적으로 받을 방법이 없다. 그렇다고
 * 토큰을 발급해주는 개발용 엔드포인트를 만들면, 아무나 원하는 role로 토큰을 받는
 * 백도어가 배포물에 들어간다. 그래서 테스트로 뽑아 쓴다 — src/test라 WAR에 없다.
 *
 * 사용법:
 *   ./gradlew test --tests "*TokenPrinterTest" --rerun-tasks -i
 *   출력된 토큰을 복사해 Authorization: Bearer <토큰> 헤더에 넣는다.
 *
 * 주의: 토큰이 통하려면 '앱이 실제로 쓰는 시크릿'과 같은 키로 서명돼야 한다.
 * 그래서 JWT_SECRET 환경변수가 있으면 그걸 먼저 쓴다. 환경변수를 설정해 앱을 띄웠는데
 * 여기서 기본값으로 서명하면 서명 불일치로 401이 난다.
 */
class TokenPrinterTest {

    /** application.properties의 jwt.secret 기본값과 동일. 환경변수가 있으면 그것을 쓴다. */
    private static final String DEV_DEFAULT_SECRET = "roc9Ns8gE2EDKDkYXuy/tHxrKZXoeaWHTMb+eN8YeZM=";
    private static final String SECRET = resolveSecret();

    private static final long ACCESS_MS = 1_800_000L;      // 30분
    private static final long REFRESH_MS = 1_209_600_000L; // 14일

    /** sql/seed/01_seed_valid_data.sql 의 비밀번호. 해시를 바꾸려면 이 값을 고치고 다시 실행한다. */
    private static final String SEED_PASSWORD = "Local1234!";

    private final JwtProvider provider = new JwtProvider(SECRET, ACCESS_MS, REFRESH_MS);

    private static String resolveSecret() {
        String fromEnv = System.getenv("JWT_SECRET");
        return (fromEnv == null || fromEnv.isBlank()) ? DEV_DEFAULT_SECRET : fromEnv;
    }

    @Test
    @DisplayName("수동 테스트용 토큰을 출력한다")
    void printTokens() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, -60_000L, -60_000L);
        JwtProvider otherKeyProvider =
                new JwtProvider("nDlRA4wqNsD9UWmGExA1MCPvrWiVob6ewIO9ss319jY=", ACCESS_MS, REFRESH_MS);

        boolean usingEnv = !SECRET.equals(DEV_DEFAULT_SECRET);

        System.out.println();
        System.out.println("=".repeat(74));
        System.out.println(" 수동 테스트용 토큰 (Access 30분)");
        System.out.println(" 시크릿 출처: " + (usingEnv ? "JWT_SECRET 환경변수" : "개발 기본값(application.properties)"));
        System.out.println("=".repeat(74));

        // memberId는 sql/seed/01_seed_valid_data.sql 의 회원과 맞춘다.
        //   id=1 김부모(PARENT) / id=2 김첫째(CHILD) / id=3 김둘째(CHILD)
        print("PARENT Access (id=1, 정상)", provider.createAccessToken(1L, "PARENT"),
                "보호 API 200, 부모전용 200");
        print("CHILD Access (id=2, 정상)", provider.createAccessToken(2L, "CHILD"),
                "보호 API 200, 부모전용 403");
        print("만료된 Access", expiredProvider.createAccessToken(1L, "PARENT"),
                "401 AUTH_TOKEN_EXPIRED");
        print("위조 (다른 키 서명)", otherKeyProvider.createAccessToken(1L, "PARENT"),
                "401 AUTH_TOKEN_INVALID");
        print("Refresh (오용)", provider.createRefreshToken(1L),
                "401 AUTH_TOKEN_INVALID");

        System.out.println();
        System.out.println("-".repeat(74));
        System.out.println(" 확인할 경로");
        System.out.println("   GET /api/v1/health              공개 — 토큰 없이 200");
        System.out.println("   GET /api/v1/members/me          인증 필요 (하위3에서 구현)");
        System.out.println();
        System.out.println(" curl 예시");
        System.out.println("   curl -i -H \"Authorization: Bearer <위 토큰>\" \\");
        System.out.println("        http://localhost:8080/api/v1/members/me");
        System.out.println("=".repeat(74));
        System.out.println();
    }

    @Test
    @DisplayName("seed SQL에 넣을 BCrypt 비밀번호 해시를 출력한다")
    void printSeedPasswordHash() {
        // BCrypt는 salt가 매번 달라 실행마다 다른 해시가 나온다. 둘 다 유효하다.
        // seed 파일의 해시를 교체하고 싶을 때만 여기 출력을 쓰면 된다.
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(SEED_PASSWORD);

        System.out.println();
        System.out.println("=".repeat(74));
        System.out.println(" seed용 BCrypt 해시  (sql/seed/01_seed_valid_data.sql)");
        System.out.println("=".repeat(74));
        System.out.printf(" 평문 : %s%n", SEED_PASSWORD);
        System.out.printf(" 해시 : %s%n", hash);
        System.out.printf(" 길이 : %d자 (T_MBR_INFO_M.password VARCHAR(255)에 들어간다)%n", hash.length());
        System.out.println("=".repeat(74));
        System.out.println();

        // 생성한 해시가 실제로 그 비밀번호와 맞는지는 확인해 둔다.
        // 여기가 깨지면 seed의 해시로 로그인이 안 된다는 뜻이다.
        org.junit.jupiter.api.Assertions.assertTrue(encoder.matches(SEED_PASSWORD, hash));
    }

    private void print(String label, String token, String expected) {
        System.out.println();
        System.out.printf("[%s]  기대: %s%n", label, expected);
        System.out.println(token);
    }
}
