package com.teenyfin.teenymoney.domain.financialproduct.finlife.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinlifeApiResponseDTO {

    private Result result;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("err_cd")
        private String errorCode;
        @JsonProperty("err_msg")
        private String errorMessage;
        @JsonProperty("max_page_no")
        private Integer maxPageNumber;
        @JsonProperty("now_page_no")
        private Integer currentPageNumber;
        @JsonProperty("total_count")
        private Integer totalCount;
        private List<FinlifeProductBaseDTO> baseList = new ArrayList<>();
        private List<FinlifeProductOptionDTO> optionList = new ArrayList<>();
    }
}
