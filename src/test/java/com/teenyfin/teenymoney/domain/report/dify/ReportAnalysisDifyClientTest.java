package com.teenyfin.teenymoney.domain.report.dify;

import com.teenyfin.teenymoney.domain.report.dify.dto.DifyReportAnalysisResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// domain/chatbot/dify/DifyClientTest와 같은 구성 - Dify 워크플로우 API 전용 클라이언트라
// 요청/응답 DTO 타입만 다르고 흐름(fail-fast, 4xx/5xx, 네트워크 실패)은 동일하다.
class ReportAnalysisDifyClientTest {

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
    }

    @Test
    @DisplayName("api-key가 null이거나 공백이면 isConfigured()는 false다")
    void isConfiguredIsFalseWhenApiKeyMissing() {
        assertFalse(client(null).isConfigured());
        assertFalse(client("   ").isConfigured());
    }

    @Test
    @DisplayName("api-key가 실제로 있으면 isConfigured()는 true다")
    void isConfiguredIsTrueWhenApiKeyPresent() {
        assertTrue(client("real-api-key").isConfigured());
    }

    @Test
    @DisplayName("api-key가 없으면 RestTemplate을 건드리지도 않고 바로 막는다")
    void analyzeFailsFastWhenApiKeyMissing() {
        ReportAnalysisDifyClient client = client("");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.analyze("{\"thisMonth\":{}}", "member-1"));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_API_KEY_MISSING, exception.getErrorCode());
        // 키가 없다는 걸 확인한 시점에서 바로 던져야지, 네트워크 호출까지 나가면 안 된다
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Dify가 정상 응답을 주면 outputs.text를 그대로 돌려준다")
    void analyzeReturnsTextOnSuccess() {
        ReportAnalysisDifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyReportAnalysisResponseDTO.class)))
                .thenReturn(difyResponse("succeeded", "이번 달엔 용돈을 받은 날 크게 쓰는 습관이 보였어요."));

        String analysis = client.analyze("{\"thisMonth\":{}}", "member-1");

        assertEquals("이번 달엔 용돈을 받은 날 크게 쓰는 습관이 보였어요.", analysis);
    }

    @Test
    @DisplayName("200을 받았는데 body 자체가 없으면 응답이 이상하다고 판단한다")
    void analyzeFailsWhenResponseBodyMissing() {
        ReportAnalysisDifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyReportAnalysisResponseDTO.class)))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.analyze("{\"thisMonth\":{}}", "member-1"));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("200을 받았는데 outputs.text가 없으면 응답이 이상하다고 판단한다")
    void analyzeFailsWhenTextOutputMissing() {
        ReportAnalysisDifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyReportAnalysisResponseDTO.class)))
                .thenReturn(difyResponse("succeeded", null));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.analyze("{\"thisMonth\":{}}", "member-1"));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("outputs.text가 빈 문자열이어도 응답이 이상하다고 판단한다")
    void analyzeFailsWhenTextOutputBlank() {
        ReportAnalysisDifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyReportAnalysisResponseDTO.class)))
                .thenReturn(difyResponse("succeeded", "   "));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.analyze("{\"thisMonth\":{}}", "member-1"));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("Dify가 4xx/5xx로 응답하면 MONEY_REPORT_ANALYSIS_REQUEST_FAILED로 변환한다")
    void analyzeFailsWhenDifyReturnsErrorStatus() {
        ReportAnalysisDifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyReportAnalysisResponseDTO.class)))
                .thenThrow(new HttpClientErrorException(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        "{\"code\":\"invalid_param\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.analyze("{\"thisMonth\":{}}", "member-1"));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_REQUEST_FAILED, exception.getErrorCode());
    }

    @Test
    @DisplayName("타임아웃/연결 실패 등 네트워크 자체가 안 되면 MONEY_REPORT_ANALYSIS_REQUEST_FAILED로 변환한다")
    void analyzeFailsWhenNetworkCallFails() {
        ReportAnalysisDifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyReportAnalysisResponseDTO.class)))
                .thenThrow(new RestClientException("Connection timed out"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.analyze("{\"thisMonth\":{}}", "member-1"));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_REQUEST_FAILED, exception.getErrorCode());
    }

    private ReportAnalysisDifyClient client(String apiKey) {
        return new ReportAnalysisDifyClient(restTemplate, apiKey, "https://api.dify.ai/v1");
    }

    // DifyReportAnalysisResponseDTO/Data는 NoArgsConstructor + Setter라서(Jackson 역직렬화용),
    // 테스트에서도 같은 방식으로 만든다. text가 null이면 outputs 자체를 비워서
    // "출력 변수가 아예 없는" 상황도 같이 흉내낸다.
    private DifyReportAnalysisResponseDTO difyResponse(String status, String text) {
        DifyReportAnalysisResponseDTO response = new DifyReportAnalysisResponseDTO();
        DifyReportAnalysisResponseDTO.Data data = new DifyReportAnalysisResponseDTO.Data();
        data.setStatus(status);
        data.setOutputs(text == null ? Map.of() : Map.of("text", text));
        response.setData(data);
        return response;
    }
}
