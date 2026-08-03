package com.teenyfin.teenymoney.domain.wallet.controller;


import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.service.WalletService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
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
}
