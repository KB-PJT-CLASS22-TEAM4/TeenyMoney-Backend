package com.teenyfin.teenymoney.global.response;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 응답 껍데기 계약이 깨지지 않는지 확인한다.
 * DB도 톰캣도 필요 없다 (standaloneSetup). 다른 테스트와 달리 항상 돌아간다.
 */
class ApiResponseFormatTest {

    @RestController
    static class StubController {
        @GetMapping("/ok")
        ApiResponse<String> ok() {
            return ApiResponse.ok("hello");
        }

        @GetMapping("/fail")
        ApiResponse<Void> fail() {
            throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("성공 응답은 success=true, code=OK, data 포함")
    void successEnvelope() throws Exception {
        String body = mockMvc.perform(get("/ok"))
                .andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("\"code\":\"OK\""), body);
        assertTrue(body.contains("\"data\":\"hello\""), body);
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태 코드와 code 문자열로 변환된다")
    void errorEnvelope() throws Exception {
        var response = mockMvc.perform(get("/fail")).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(403, response.getStatus());
        assertTrue(body.contains("\"success\":false"), body);
        assertTrue(body.contains("\"code\":\"AUTH_FORBIDDEN\""), body);
        assertTrue(body.contains("\"message\":\"접근 권한이 없습니다.\""), body);
    }

    @Test
    @DisplayName("PageResponse의 totalPages는 올림 계산된다")
    void pageResponseTotalPages() {
        assertEquals(3, PageResponse.of(java.util.List.of(), 1, 20, 57).getTotalPages());
        assertEquals(0, PageResponse.of(java.util.List.of(), 1, 20, 0).getTotalPages());
        assertEquals(1, PageResponse.of(java.util.List.of(), 1, 20, 20).getTotalPages());
    }
}
