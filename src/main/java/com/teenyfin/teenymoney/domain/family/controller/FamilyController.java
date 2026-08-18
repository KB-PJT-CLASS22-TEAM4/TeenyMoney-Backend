package com.teenyfin.teenymoney.domain.family.controller;

import com.teenyfin.teenymoney.domain.family.dto.request.FamilyLinkRequestDTO;
import com.teenyfin.teenymoney.domain.family.dto.response.FamilyLinkCodeResponseDTO;
import com.teenyfin.teenymoney.domain.family.service.FamilyLinkCodeService;
import com.teenyfin.teenymoney.domain.family.service.FamilyUnlinkService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/families")
@Api(tags = "Families", description = "가족 연동 API")
public class FamilyController {

    private final FamilyLinkCodeService familyLinkCodeService;
    private final FamilyUnlinkService familyUnlinkService;

    public FamilyController(FamilyLinkCodeService familyLinkCodeService,
                            FamilyUnlinkService familyUnlinkService) {
        this.familyLinkCodeService = familyLinkCodeService;
        this.familyUnlinkService = familyUnlinkService;
    }

    @PostMapping("/make-codes")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "가족 연동 코드 발급",
            notes = "부모 회원에게 10분간 유효한 6자리 가족 연동 코드를 발급합니다. "
                    + "재발급하면 직전에 발급한 코드는 즉시 무효가 되며, "
                    + "부모당 유효한 코드는 항상 하나입니다.\n\n"
                    + "Idempotency-Key 헤더는 필수입니다. '발급 의도'마다 고유한 값(UUID)을 보내십시오. "
                    + "같은 키로 다시 호출하면 새 코드를 만들지 않고 그때 발급한 코드를 그대로 돌려줍니다. "
                    + "타임아웃 재시도에 같은 키를 재사용해야, 늦게 도착한 첫 응답이 "
                    + "이미 무효가 된 코드를 화면에 남기지 않습니다. "
                    + "사용자가 새 코드를 원할 때만 새 키를 만드십시오.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(
                    code = 200,
                    message = "연동 코드 발급 성공"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 400,
                    message = "Idempotency-Key 헤더가 없거나 값이 올바르지 않음"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 401,
                    message = "로그인이 필요함"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 403,
                    message = "부모 회원이 아님"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 429,
                    message = "직전 발급 직후라 잠시 후 재시도해야 함"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 503,
                    message = "연동 코드를 발급할 수 없음"
            )
    })
    public ApiResponse<FamilyLinkCodeResponseDTO> makeLinkCode(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ApiResponse.ok(
                familyLinkCodeService.makeCode(principal.memberId(), idempotencyKey)
        );
    }

    @PostMapping("/connect-link")
    @PreAuthorize("hasRole('CHILD')")
    @ApiOperation(
            value = "가족 연동 코드 소비",
            notes = "자녀가 부모의 6자리 연동 코드를 소비하고 가족 관계를 생성합니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "가족 연결 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "만료·사용·형식 오류 코드 또는 유효하지 않은 부모"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "자녀 회원이 아님"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이미 가족과 연결된 자녀"),
            @io.swagger.annotations.ApiResponse(code = 429, message = "코드 입력 시도 횟수 초과"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "예상하지 못한 DB 저장 실패"),
            @io.swagger.annotations.ApiResponse(code = 503, message = "Redis 저장소 일시 장애")
    })
    public ApiResponse<Void> linkFamily(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody FamilyLinkRequestDTO request) {
        familyLinkCodeService.linkChild(principal.memberId(), request.getCode());
        return ApiResponse.ok();
    }

    @DeleteMapping("/unlink/{childId}")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "가족 연동 해제",
            notes = "부모가 자녀와의 연동을 해제합니다.\n\n"
                    + "자녀는 이 API를 호출할 수 없습니다(403). 자녀가 스스로 연동을 끊을 수 있으면 "
                    + "보호자 감독이라는 기능의 전제가 무너지기 때문입니다. 해제를 원하는 자녀는 "
                    + "고객센터로 문의해야 하며, 자녀 화면에는 해제 버튼 대신 그 안내를 노출합니다.\n\n"
                    + "해제하면 그 자녀의 정기 용돈 스케줄이 중지되고, 진행 중이던 퀘스트가 "
                    + "마감됩니다. 이미 완료된 이력과 지갑 잔액, 티니점수는 그대로 남습니다. "
                    + "부모가 설정해 둔 업종 정책도 남으므로, 나중에 다시 연동하면 그대로 복원됩니다.\n\n"
                    + "상환하지 않은 대출이 있으면 해제할 수 없습니다. 부모가 채권자이기 때문입니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "연동 해제 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "부모 회원이 아니거나 내 자녀가 아님"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "연동된 가족이 아니거나 미상환 대출이 있음")
    })
    public ApiResponse<Void> unlinkFamily(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId) {
        familyUnlinkService.unlink(principal, childId);
        return ApiResponse.ok();
    }
}
