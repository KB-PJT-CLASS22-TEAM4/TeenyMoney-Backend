package com.teenyfin.teenymoney.domain.wallet.controller;


import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionPeriod;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionSortOrder;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionTypeFilter;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletTransactionResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.service.WalletService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
// TODO(springdoc 의존성 추가 후 주석 해제):
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.Parameter;
// import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/wallet")
// @Tag(name = "Wallet", description = "지갑 잔액과 거래내역")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }


    // JwtAuthenticationFilter가 토큰을 검증하면서 SecurityContext에 넣어둔
    // MemberPrincipal(memberId, role)을 바로 꺼내온다. DB 조회 없이 토큰에서만 나옴.
    @GetMapping("/me")
    public ApiResponse<WalletDetailResponseDTO> getMyWalletDetail(@AuthenticationPrincipal MemberPrincipal principal) {
        // Controller는 memberId만 꺼내서 Service에 넘기고, 실제 판단(지갑 찾기, 없으면 에러 등)은
        // 전부 Service가 한다 — Controller는 HTTP 입출력만 담당.
        WalletDetailResponseDTO response = walletService.getMyWalletDetail(principal.memberId());

        // 모든 API 응답은 ApiResponse<T>로 감싼다 (success/code/message/data 형태)
        return ApiResponse.ok(response);
    }

    // 거래내역 목록 조회. 종류(전체/입금/출금) / 기간(1주~6개월) / 정렬(최신순/과거순) 버튼
    // 3개 상태를 쿼리 파라미터로 받는다. 아무 버튼도 안 누른 최초 진입 상태의 기본값은
    // "전체 / 1개월 / 최신순"으로 정했다.
    //
    // TODO(springdoc 의존성 추가 후 주석 해제):
    // @Operation(
    //         summary = "내 거래내역 목록 조회",
    //         description = "종류(type)/기간(period)/정렬(sort) 세 조건을 조합해서 필터링한다. "
    //                 + "파라미터를 안 보내면 기본값(전체/1개월/최신순)이 적용된다."
    // )
    @GetMapping("/me/transactions")
    public ApiResponse<List<WalletTransactionResponseDTO>> getMyTransactions(
            @AuthenticationPrincipal MemberPrincipal principal,
            // @Parameter(description = "거래 종류 필터: ALL(전체)/CREDIT(입금)/DEBIT(출금)")
            @RequestParam(defaultValue = "ALL") TransactionTypeFilter type,
            // @Parameter(description = "조회 기간: WEEK(1주일)/MONTH(1개월)/THREE_MONTHS(3개월)/SIX_MONTHS(6개월)")
            @RequestParam(defaultValue = "MONTH") TransactionPeriod period,
            // @Parameter(description = "정렬 방향: DESC(최신순)/ASC(과거순)")
            @RequestParam(defaultValue = "DESC")TransactionSortOrder sort) {
        List<WalletTransactionResponseDTO> response = walletService.getMyTransactions(principal.memberId(), type, period, sort);
        return ApiResponse.ok(response);
    }
}
