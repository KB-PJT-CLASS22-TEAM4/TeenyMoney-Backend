package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.DepositEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingPaymentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEarlyRepaymentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentRequestResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.SavingPaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductTerminationResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.LoanEarlyRepaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductEnrollmentService;
import com.teenyfin.teenymoney.domain.financialproduct.service.FreeSavingPaymentService;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductTerminationService;
import com.teenyfin.teenymoney.domain.financialproduct.service.LoanEarlyRepaymentService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/financial-products")
@Api(tags = "Financial Product Enrollment", description = "자녀 금융상품 가입 요청")
public class FinancialProductEnrollmentController {
    private final FinancialProductEnrollmentService enrollmentService;
    private final FreeSavingPaymentService freeSavingPaymentService;
    private final FinancialProductTerminationService terminationService;
    private final LoanEarlyRepaymentService loanEarlyRepaymentService;

    public FinancialProductEnrollmentController(
            FinancialProductEnrollmentService enrollmentService,
            FreeSavingPaymentService freeSavingPaymentService,
            FinancialProductTerminationService terminationService,
            LoanEarlyRepaymentService loanEarlyRepaymentService) {
        this.enrollmentService = enrollmentService;
        this.freeSavingPaymentService = freeSavingPaymentService;
        this.terminationService = terminationService;
        this.loanEarlyRepaymentService = loanEarlyRepaymentService;
    }

    @PostMapping("/deposit-enrollments")
    @ApiOperation(value = "예금 가입 요청")
    public ApiResponse<FinancialProductEnrollmentRequestResponseDTO> requestDeposit(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody DepositEnrollmentRequestDTO request) {
        return ApiResponse.ok(enrollmentService.requestDeposit(principal, request));
    }

    @PostMapping("/saving-enrollments")
    @ApiOperation(value = "적금 가입 요청")
    public ApiResponse<FinancialProductEnrollmentRequestResponseDTO> requestSaving(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody SavingEnrollmentRequestDTO request) {
        return ApiResponse.ok(enrollmentService.requestSaving(principal, request));
    }

    @PostMapping("/loan-enrollments")
    @ApiOperation(value = "대출 가입 요청")
    public ApiResponse<FinancialProductEnrollmentRequestResponseDTO> requestLoan(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody LoanEnrollmentRequestDTO request) {
        return ApiResponse.ok(enrollmentService.requestLoan(principal, request));
    }

    @PostMapping("/saving-enrollments/{enrollmentId}/payments")
    @ApiOperation(value = "자유적금 직접납입",
            notes = "자녀 지갑에서 자유적금 지갑으로 납입하며 UUID 재요청은 중복 출금되지 않습니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "납입 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 형식 오류, 정기적금 또는 월 한도 초과"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "자녀 회원이 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인의 적금 가입 정보를 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "비활성 가입 또는 멱등성 키 충돌")
    })
    public ApiResponse<SavingPaymentResponseDTO> payFreeSaving(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long enrollmentId,
            @Valid @RequestBody SavingPaymentRequestDTO request) {
        return ApiResponse.ok(
                freeSavingPaymentService.pay(principal, enrollmentId, request));
    }

    @GetMapping("/{productType}-enrollments/{enrollmentId}/termination-quote")
    @ApiOperation(value = "예·적금 중도해지 예상 조회",
            notes = "현재 시점의 진행률, 적용금리, 원금, 이자, 최종 지급액과 점수 변화를 조회합니다.")
    public ApiResponse<FinancialProductTerminationResponseDTO> getTerminationQuote(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String productType,
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(
                terminationService.quote(principal, productType, enrollmentId));
    }

    @PostMapping("/{productType}-enrollments/{enrollmentId}/terminate")
    @ApiOperation(value = "예·적금 중도해지 실행",
            notes = "부모 승인 없이 원금과 중도해지 이자를 지급하고 티니점수를 한 번만 반영합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "중도해지 성공"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "자녀 회원이 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인의 가입 계약을 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "비활성 또는 만기 도달 계약")
    })
    public ApiResponse<FinancialProductTerminationResponseDTO> terminate(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String productType,
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(
                terminationService.terminate(principal, productType, enrollmentId));
    }

    // 예·적금 중도해지의 termination-quote/terminate 패턴을 그대로 따른다: 조회는 GET, 실행은 POST.
    // 대출은 전액이 아니라 자녀가 원하는 금액을 매번 다르게 넣을 수 있어 amount를 함께 받는다.
    // GET은 관례상 body를 안 써서 amount를 쿼리파라미터로 받고, 지갑/DB는 건드리지 않는다.
    @GetMapping("/loan-enrollments/{enrollmentId}/early-repayment-quote")
    @ApiOperation(value = "대출 조기상환 예상 조회",
            notes = "요청 금액이 연체이자와 원금에 각각 얼마나 충당되는지, 완제 시 상태와 점수 변화를 조회합니다.")
    public ApiResponse<LoanEarlyRepaymentResponseDTO> getLoanEarlyRepaymentQuote(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long enrollmentId,
            @RequestParam @Positive Long amount) {
        return ApiResponse.ok(
                loanEarlyRepaymentService.quote(principal, enrollmentId, amount));
    }

    // 실행은 실제 송금을 일으키므로 body(JSON)로 amount와 재시도 방지용 idempotencyKey를 함께 받는다.
    @PostMapping("/loan-enrollments/{enrollmentId}/early-repayment")
    @ApiOperation(value = "대출 조기상환 실행",
            notes = "요청 금액을 연체이자부터 충당한 뒤 남은 만큼 원금을 줄이며, "
                    + "잔여 원금이 0원이 되면 대출을 자동으로 종료합니다. UUID 재요청은 중복 출금되지 않습니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조기상환 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 형식 오류 또는 남은 상환 금액 초과"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "자녀 회원이 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인의 대출 가입 정보를 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "상환 중인 대출이 아니거나 잔액 부족, 멱등성 키 충돌")
    })
    public ApiResponse<LoanEarlyRepaymentResponseDTO> repayLoanEarly(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long enrollmentId,
            @Valid @RequestBody LoanEarlyRepaymentRequestDTO request) {
        return ApiResponse.ok(
                loanEarlyRepaymentService.repay(principal, enrollmentId, request));
    }
}
