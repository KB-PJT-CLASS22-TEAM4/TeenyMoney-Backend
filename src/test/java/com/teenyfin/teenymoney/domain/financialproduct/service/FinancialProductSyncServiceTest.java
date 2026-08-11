package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.finlife.FinlifeClient;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeApiResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeProductBaseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeProductOptionDTO;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
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
        assertEquals("SIMPLE",
                captor.getValue().getInterestCalculationType());
        assertEquals("이자를 원금에 합산하지 않고 계산하는 단리 예금",
                captor.getValue().getDescription());
        assertEquals("0010927", captor.getValue().getFinancialCompanyCode());
    }

    @Test
    void savingUsesOneCoherentOptionProfile() {
        FinlifeApiResponseDTO.Result result = new FinlifeApiResponseDTO.Result();
        result.setBaseList(List.of(base()));
        result.setOptionList(List.of(
                option("12", "3.50", "F", "S"),
                option("6", "4.00", "S", "M"),
                option("12", "4.20", "S", "M")));
        when(client.fetchSavingProducts()).thenReturn(result);
        when(mapper.upsertSavingProduct(any())).thenReturn(1);

        service.syncSavingProducts();

        ArgumentCaptor<SavingProductVO> captor =
                ArgumentCaptor.forClass(SavingProductVO.class);
        verify(mapper).upsertSavingProduct(captor.capture());
        SavingProductVO product = captor.getValue();
        assertEquals("FREE", product.getSavingsType());
        assertEquals("COMPOUND", product.getInterestCalculationType());
        assertEquals("원하는 금액을 자유롭게 납입하는 복리 자유적금",
                product.getDescription());
        assertEquals(new BigDecimal("4.00"), product.getRate6m());
        assertEquals(new BigDecimal("4.20"), product.getRate12m());
    }

    @Test
    void fixedSimpleSavingGetsMatchingDescription() {
        FinlifeApiResponseDTO.Result result = new FinlifeApiResponseDTO.Result();
        result.setBaseList(List.of(base()));
        result.setOptionList(List.of(option("12", "3.50", "F", "S")));
        when(client.fetchSavingProducts()).thenReturn(result);
        when(mapper.upsertSavingProduct(any())).thenReturn(1);

        service.syncSavingProducts();

        ArgumentCaptor<SavingProductVO> captor =
                ArgumentCaptor.forClass(SavingProductVO.class);
        verify(mapper).upsertSavingProduct(captor.capture());
        assertEquals("매월 정해진 금액을 납입하는 단리 정액적금",
                captor.getValue().getDescription());
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
        return option(term, rate, null, null);
    }

    private FinlifeProductOptionDTO option(
            String term,
            String rate,
            String reserveType,
            String interestRateType) {
        FinlifeProductOptionDTO option = new FinlifeProductOptionDTO();
        option.setFinancialCompanyCode("0010927");
        option.setFinancialProductCode("WR0001B");
        option.setSavingTerm(term);
        option.setInterestRate(new BigDecimal(rate));
        option.setReserveType(reserveType);
        option.setInterestRateType(interestRateType);
        return option;
    }
}
