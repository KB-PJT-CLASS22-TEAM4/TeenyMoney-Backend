package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.finlife.FinlifeClient;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeApiResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeProductBaseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeProductOptionDTO;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialProductSyncServiceTest {

    private FinlifeClient client;
    private FinancialProductMapper mapper;
    private FinancialProductSyncService service;

    @BeforeEach
    void setUp() {
        client = mock(FinlifeClient.class);
        mapper = mock(FinancialProductMapper.class);
        service = new FinancialProductSyncService(client, mapper);
    }

    @Test
    void syncKeepsOnlySupportedTerms() {
        FinlifeApiResponseDTO.Result result = new FinlifeApiResponseDTO.Result();
        result.setBaseList(List.of(base()));
        result.setOptionList(List.of(
                option("12", "3.50"),
                option("24", "4.00")));
        when(client.fetchDepositProducts()).thenReturn(result);
        when(mapper.upsertDepositProduct(any())).thenReturn(1);

        int affected = service.syncDepositProducts();

        ArgumentCaptor<DepositProductVO> captor =
                ArgumentCaptor.forClass(DepositProductVO.class);
        verify(mapper).upsertDepositProduct(captor.capture());
        assertEquals(1, affected);
        assertEquals(new BigDecimal("3.50"), captor.getValue().getRate12m());
        assertNull(captor.getValue().getRate1m());
        assertEquals("0010927", captor.getValue().getFinancialCompanyCode());
    }

    private FinlifeProductBaseDTO base() {
        FinlifeProductBaseDTO base = new FinlifeProductBaseDTO();
        base.setFinancialCompanyCode("0010927");
        base.setFinancialProductCode("WR0001B");
        base.setFinancialCompanyName("국민은행");
        base.setProductName("정기예금");
        return base;
    }

    private FinlifeProductOptionDTO option(String term, String rate) {
        FinlifeProductOptionDTO option = new FinlifeProductOptionDTO();
        option.setFinancialCompanyCode("0010927");
        option.setFinancialProductCode("WR0001B");
        option.setSavingTerm(term);
        option.setInterestRate(new BigDecimal(rate));
        return option;
    }
}
