package com.teenyfin.teenymoney.domain.member.controller;

import com.teenyfin.teenymoney.domain.member.dto.response.AgreementResponseDTO;
import com.teenyfin.teenymoney.domain.member.service.TermsService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 실제 경로는 /api/v1/terms. 접두어는 ServletConfig가 @RestController에 자동으로 붙인다.
// 인증 없이 호출할 수 있다 - 약관은 가입 전에도 보여줘야 하는 문서라서
// SecurityConfig의 PUBLIC_ENDPOINTS에 등록되어 있다.
@RestController
@RequestMapping("/terms")
@Api(tags = "Terms", description = "약관 및 정책 조회 API")
public class TermsController {

    private final TermsService termsService;

    public TermsController(TermsService termsService) {
        this.termsService = termsService;
    }

    @GetMapping
    @ApiOperation(
            value = "유효 약관 목록 조회",
            notes = "현재 적용 기간에 포함되는 약관 목록을 조회합니다. 로그인 없이 호출할 수 있습니다.\n\n"
                    + "**응답의 content는 항상 null입니다.** 약관 전문은 길어서 목록에 싣지 않습니다. "
                    + "전문이 필요하면 code로 상세 조회를 호출하세요.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조회 성공")
    })
    public ApiResponse<List<AgreementResponseDTO>> getTerms() {
        return ApiResponse.ok(termsService.getEffectiveTerms());
    }

    @GetMapping("/{code}")
    @ApiOperation(
            value = "약관 전문 조회",
            notes = "약관 코드로 현재 유효한 버전의 전문을 조회합니다. 로그인 없이 호출할 수 있습니다.\n\n"
                    + "유효한 버전이 여러 개면 가장 최근에 적용된 것을 반환합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "해당 코드의 유효한 약관 없음")
    })
    public ApiResponse<AgreementResponseDTO> getTerms(
            @ApiParam(value = "약관 코드", example = "SERVICE_TERMS", required = true)
            @PathVariable("code") String code) {
        return ApiResponse.ok(termsService.getEffectiveTerms(code));
    }
}
