package com.teenyfin.teenymoney.domain.report.dify;

import com.teenyfin.teenymoney.domain.report.dify.dto.DifyReportAnalysisRequestDTO;
import com.teenyfin.teenymoney.domain.report.dify.dto.DifyReportAnalysisResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

// Dify 워크플로우 API(/v1/workflows/run)와 통신하는 담당 클래스.
// domain/chatbot/dify/DifyClient와 뼈대(fail-fast, Bearer 인증, RestTemplate)는 같지만,
// 챗봇용 Dify 앱과는 완전히 별개의 앱이라 API 키를 따로 관리한다
// (dify.report-api-key - dify.api-key와 다른 값. base-url은 공용).
@Slf4j
@Component
public class ReportAnalysisDifyClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;


    public ReportAnalysisDifyClient(RestTemplate restTemplate, @Value("${dify.report-api-key}")String apiKey, @Value("${dify.base-url:https://api.dify.ai/v1}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // reportDataJson: 이번 달+지난달 원본 데이터를 담은 JSON 문자열 (ReportAnalysisService가 조립)
    // user: Dify 쪽 사용자 식별자 ("member-{memberId}")

    public String analyze(String reportDataJson, String user) {

        if(!isConfigured()) {
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_API_KEY_MISSING);
        }

        String url = baseUrl + "/workflows/run";
        DifyReportAnalysisRequestDTO request = new DifyReportAnalysisRequestDTO(reportDataJson, user);
        HttpEntity<DifyReportAnalysisRequestDTO> entity = new HttpEntity<>(request, buildAuthHeaders());



        try {
            DifyReportAnalysisResponseDTO response = restTemplate.postForObject(url, entity, DifyReportAnalysisResponseDTO.class);

            String analysis = response == null ? null : response.extractAnalysisText();
            if(analysis == null || analysis.isBlank()) {
                throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_RESPONSE_INVALID);
            }
            return analysis;
        } catch ( HttpStatusCodeException exception) {

            log.warn("Dify 리포트 분석 API 오류 - status: {}", exception.getStatusCode());
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_REQUEST_FAILED);
        } catch ( RestClientException exception) {
            log.warn("Dify 리포트 분석 API 호출 실패", exception);
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_REQUEST_FAILED);
        }
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }



}
