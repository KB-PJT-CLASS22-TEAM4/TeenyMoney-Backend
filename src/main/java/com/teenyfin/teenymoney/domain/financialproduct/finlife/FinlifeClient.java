package com.teenyfin.teenymoney.domain.financialproduct.finlife;

import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeApiResponseDTO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;

@Component
public class FinlifeClient {

    private static final String SUCCESS_CODE = "000";
    private static final int TIMEOUT_MILLISECONDS = 5_000;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String topFinancialGroupNumber;

    @Autowired
    public FinlifeClient(
            @Value("${finlife.api-key:}") String apiKey,
            @Value("${finlife.base-url:https://finlife.fss.or.kr/finlifeapi}")
            String baseUrl,
            @Value("${finlife.top-fin-group-no:020000}")
            String topFinancialGroupNumber) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLISECONDS);
        requestFactory.setReadTimeout(TIMEOUT_MILLISECONDS);
        this.restTemplate = new RestTemplate(requestFactory);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.topFinancialGroupNumber = topFinancialGroupNumber;
    }

    FinlifeClient(
            RestTemplate restTemplate,
            String apiKey,
            String baseUrl,
            String topFinancialGroupNumber) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.topFinancialGroupNumber = topFinancialGroupNumber;
    }

    public FinlifeApiResponseDTO.Result fetchDepositProducts() {
        return fetchAllPages("depositProductsSearch");
    }

    public FinlifeApiResponseDTO.Result fetchSavingProducts() {
        return fetchAllPages("savingProductsSearch");
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private FinlifeApiResponseDTO.Result fetchAllPages(String endpoint) {
        if (!isConfigured()) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINLIFE_API_KEY_MISSING);
        }

        FinlifeApiResponseDTO.Result merged = new FinlifeApiResponseDTO.Result();
        merged.setBaseList(new ArrayList<>());
        merged.setOptionList(new ArrayList<>());

        int pageNumber = 1;
        int maxPageNumber;
        do {
            FinlifeApiResponseDTO.Result page = fetchPage(endpoint, pageNumber);
            if (page.getBaseList() != null) {
                merged.getBaseList().addAll(page.getBaseList());
            }
            if (page.getOptionList() != null) {
                merged.getOptionList().addAll(page.getOptionList());
            }
            maxPageNumber = page.getMaxPageNumber() == null
                    ? 1 : page.getMaxPageNumber();
            pageNumber++;
        } while (pageNumber <= maxPageNumber);

        merged.setErrorCode(SUCCESS_CODE);
        merged.setMaxPageNumber(maxPageNumber);
        return merged;
    }

    private FinlifeApiResponseDTO.Result fetchPage(
            String endpoint,
            int pageNumber) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/" + endpoint + ".json")
                .queryParam("auth", apiKey)
                .queryParam("topFinGrpNo", topFinancialGroupNumber)
                .queryParam("pageNo", pageNumber)
                .build(true)
                .toUriString();

        try {
            FinlifeApiResponseDTO response = restTemplate.getForObject(
                    url, FinlifeApiResponseDTO.class);
            if (response == null || response.getResult() == null) {
                throw new BusinessException(
                        FinancialProductErrorCode.FINLIFE_RESPONSE_INVALID);
            }
            FinlifeApiResponseDTO.Result result = response.getResult();
            if (!SUCCESS_CODE.equals(result.getErrorCode())) {
                throw new BusinessException(
                        FinancialProductErrorCode.FINLIFE_API_UNAVAILABLE);
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINLIFE_API_UNAVAILABLE);
        }
    }
}
