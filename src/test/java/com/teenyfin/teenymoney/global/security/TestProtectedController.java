package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.global.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 인증 파이프라인을 HTTP 레벨에서 확인하기 위한 테스트 전용 컨트롤러.
 *
 * src/test 에 둔다. src/main 에 두면 ROOT.war 에 포함되어 운영 서버에 배포된다.
 * 인증 없이 접근 가능한 엔드포인트가 운영에 열리는 것이므로 반드시 여기 있어야 한다.
 *
 * 경로는 @RequestMapping 없이 전체 경로를 직접 적는다. 운영에서는 ServletConfig가
 * @RestController에 /api/v1 접두사를 붙이지만, 테스트 설정은 그 규칙을 쓰지 않는다.
 */
@RestController
public class TestProtectedController {

    /** 인증이 필요한 경로. 화이트리스트에 없으므로 토큰이 있어야 접근된다. */
    @GetMapping("/api/v1/test/security/protected")
    public ApiResponse<Map<String, Object>> protectedEndpoint(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        // 필터가 SecurityContext에 담은 값이 여기까지 전달되는지 확인하는 지점이다.
        // 필터가 다른 타입을 담았다면 principal이 조용히 null이 되어 아래에서 NPE가 난다.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", principal.memberId());
        data.put("role", principal.role());
        return ApiResponse.ok(data);
    }

    /** PARENT 권한이 필요한 경로. @EnableMethodSecurity가 없으면 이 애노테이션이 무시된다. */
    @PreAuthorize("hasRole('PARENT')")
    @GetMapping("/api/v1/test/security/parent")
    public ApiResponse<String> parentOnlyEndpoint() {
        return ApiResponse.ok("parent-only");
    }

    /** 화이트리스트에 있는 공개 경로. 토큰 없이 호출돼야 한다(하위3의 로그인 자리). */
    @GetMapping("/api/v1/auth/login")
    public ApiResponse<String> publicEndpoint() {
        return ApiResponse.ok("public");
    }

    @PostMapping("/api/v1/auth/phone-verification/send")
    public ApiResponse<String> phoneVerificationEndpoint() {
        return ApiResponse.ok("public");
    }
}
