package com.teenyfin.teenymoney.domain.charge.toss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossBillingKeyResponseDTO;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossCardBillingKeyRequestDTO;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossErrorResponseDTO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 토스페이먼츠 자동결제(빌링) API와 직접 통신하는 담당 클래스.
// FinlifeClient(금감원 API 연동)랑 똑같은 역할 - "외부 API 하나를 감싸는 전용 컴포넌트".
// ChargeMethodService는 토스 API의 URL이나 인증 헤더 형식 같은 걸 전혀 몰라도 되고, 그냥 이 클래스의 메서드만 호출하면 됨.

@Slf4j          // Lombok이 log.info(...)/log.warn(...) 등을 쓸 수 있게 로거 필드를 자동 생성
@Component      // 이 클래스를 스프링 빈으로 등록 - @Autowired 없이도 생성자 주입 가능

public class TossPaymentsClient {


    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // 에러 응답 JSON 문자열을 TossErrorResponseDTO로 파싱할 때 씀
    private final String secretKey;
    private final String baseUrl;

    // RestTemplate: RestTemplateConfig가 등록해둔 공용 빈을 그대로 주입받음 (더 이상 new로 안 만듦).
    // secretKey/baseUrl: application.properties 값을 @Value로 주입받음 (이건 기존과 동일).
    // 생성자가 이제 하나뿐이라, 테스트에서도 이 생성자를 그대로 호출해서
    // new TossPaymentsClient(가짜RestTemplate, "테스트키", "테스트URL") 식으로 쓰면 됨 -
    // 예전의 package-private 전용 생성자가 더 이상 필요 없어짐.
    @Autowired
    public TossPaymentsClient(RestTemplate restTemplate, @Value("${toss.secret-key}") String secretKey, @Value("${toss.base-url:https://api.tosspayments.com}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
    }

    // 카드 정보로 빌링키를 발급받는다. (토스 API: POST /v1/billing/authorizations/card)
    // 성공하면 billingKey + 카드사명 + 마스킹된 카드번호가 담긴 응답을 돌려줌.

    public TossBillingKeyResponseDTO issueCardBillingKey(TossCardBillingKeyRequestDTO request) {
        String url = baseUrl + "/v1/billing/authorizations/card";

        // HttpEntity: "요청 body + 요청 헤더"를 한 묶음으로 들고 있는 객체.
        // RestTemplate한테 "이 body를 이 헤더랑 같이 보내라"고 알려주는 포장지 같은 것.
        HttpEntity<TossCardBillingKeyRequestDTO> entity = new HttpEntity<>(request, buildAuthHeaders());

        try {
            // postForObject(url, 보낼 것, 받을 응답 타입) - HTTP POST 요청을 보내고,
            // 응답 JSON을 자동으로 TossBillingKeyResponseDTO 객체로 변환해서 리턴해줌.
            TossBillingKeyResponseDTO response =
                    restTemplate.postForObject(url, entity, TossBillingKeyResponseDTO.class);

            if (response == null || response.getBillingKey() == null) {
                throw new BusinessException(ChargeErrorCode.TOSS_RESPONSE_INVALID);
            }

            // postForObject는 응답 body가 비어있으면 null을 리턴할 수 있음(문서화된 동작).
            // billingKey가 없는 응답도 우리 입장에선 쓸모없는 응답이라 같이 걸러냄.
            // FinlifeClient.fetchPage()가 response null 체크하는 것과 같은 이유.

            return response;
        } catch(HttpStatusCodeException exception) {
            // 토스가 200이 아닌 상태코드(4xx/5xx)로 응답하면 RestTemplate이 예외를 던짐.
            // 카드번호가 틀렸거나, 유효기간이 잘못됐거나, 카드사에서 거절한 경우 등이 전부 여기로 옴.
            logTossErrorBody(exception);

            // 토스가 보낸 원본 에러 메시지를 사용자에게 그대로 노출하지 않고,
            // 우리가 정의한 ChargeErrorCode의 메시지로 감싸서 던짐 - 실제 원인은 위 로그로만 확인.
            throw new BusinessException(ChargeErrorCode.TOSS_BILLING_KEY_ISSUE_FAILED);
        }
    }

    // 빌링키를 삭제(폐기)한다. (토스 API: DELETE /v1/billing/{billingKey})
    // 결제수단 삭제 API에서 쓸 예정. 삭제되면 이 빌링키로는 더 이상 충전 승인을 못 함.
    public void deleteBillingKey(String billingKey) {
        String url = baseUrl + "/v1/billing/" + billingKey;
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());

        try {
            // exchange를 쓰는 이유: RestTemplate엔 인증 헤더를 실어서 DELETE를 보내는
            // 편의 메서드(deleteForObject 같은 것)가 따로 없어서, 메서드/헤더/응답타입을
            // 전부 직접 지정할 수 있는 exchange()를 대신 씀.
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
        } catch (RestClientException exception) {

            log.warn("토스페이먼츠 빌링키 삭제 요청 실패 - billingKey={}", billingKey, exception);
            throw new BusinessException(ChargeErrorCode.TOSS_BILLING_KEY_DELETE_FAILED);
        }
    }

    // 토스 API를 호출할 때마다 공통으로 필요한 인증 헤더를 만든다.
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();

        // 토스는 "Basic 인증" 방식을 씀: "시크릿키:" (뒤에 콜론 필수! 비밀번호가 없다는 뜻)
        // 문자열을 Base64로 인코딩해서 Authorization 헤더에 넣어야 함. 콜론을 빠뜨리면
        // 토스가 UNAUTHORIZED_KEY 에러를 돌려줌 -
        String credentials = secretKey + ":";
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        headers.set("Authorization", "Basic " + encodedCredentials);
        headers.setContentType(MediaType.APPLICATION_JSON); // 우리가 보내는 body가 JSON이라고 알려줌

        return headers;



    }


    // 토스가 실패 응답으로 보낸 JSON({"code":"...", "message":"..."})을 파싱해서
    // 우리 서버 로그에 "진짜 실패 이유"를 남긴다. 사용자에게 보여주는 메시지가 아니라
    // 우리(개발자)가 나중에 로그 보고 디버깅할 때 쓰는 용도.
    private void logTossErrorBody(HttpStatusCodeException exception) {
        try {
            TossErrorResponseDTO error = objectMapper.readValue(exception.getResponseBodyAsString(), TossErrorResponseDTO.class);
            log.warn("토스페이먼츠 API 오류 - status: {}, code; {}, message: {}", exception.getStatusCode(), error.getCode(), error.getMessage());

        } catch (Exception parseException) {

            // 에러 응답이 예상한 JSON 형태가 아닐 수도 있는데, 그것 때문에 원래 처리가
            // 막히면 안 되니까 파싱 실패는 조용히 무시하고 원본 텍스트만 로그에 남김.
            log.warn("토스페이먼츠 API 오류 - status: {}, body: {}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
        }
    }



}
