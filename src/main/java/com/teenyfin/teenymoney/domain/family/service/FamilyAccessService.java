package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀 소유권 검증. 용돈·정책·예금·알림이 공통으로 쓴다.
 *
 * /members/me/children은 WHERE 절의 파라미터를 토큰에서 받으므로 클라이언트가 다른 가족을
 * 지목할 방법이 없다. 후속 도메인은 GET /children/{childId}/... 형태라 대상 지정이
 * 클라이언트 입력이 되고, 그 범위 고정이 쿼리에서 사라진다.
 *
 * JWT는 요청자가 누구인지만 증명하고 @PreAuthorize("hasRole('PARENT')")는 어떤 부모든
 * 통과시킨다. 따라서 타 가족 부모가 자기 정상 토큰으로 childId만 바꿔 호출하면 인증과
 * 역할 인가를 모두 통과한다. id가 BIGINT AUTO_INCREMENT라 값도 추측 가능하다.
 * 범위 고정을 암묵적(WHERE 절)에서 명시적(이 호출)으로 옮기는 것이 이 클래스의 목적이다.
 *
 * ── 사용법 ──────────────────────────────────────────────────────────────
 *
 * 판단 기준은 한 줄이다. childId를 요청에서 받았다면 메서드 첫 줄에 호출한다.
 *
 *   public AllowanceResponseDTO getAllowance(MemberPrincipal principal, Long childId) {
 *       familyAccessService.requireChildAccess(principal, childId);   // 첫 줄
 *       return allowanceMapper.selectByChildId(childId);              // 여기부턴 안전
 *   }
 *
 * 필요 여부는 URL 모양이 아니라 childId의 출처로 갈린다. 클라이언트가 값을 정하면
 * 경로든 쿼리 파라미터든 바디든 전부 조작 대상이다.
 *
 *   principal.memberId()에서 온 값   -> 불필요 (지목할 자리가 없다)
 *   URL path {childId}               -> 필수
 *   쿼리 파라미터 ?childId=          -> 필수
 *   요청 바디 {"childId": 2}         -> 필수
 *
 * 그래서 /members/me/parent 와 /members/me/children 은 이 호출이 없다. 대상이 토큰에서만
 * 나오므로 WHERE 절이 이미 범위를 고정하고 있다.
 *
 * ── 대상과 호출자는 다르다 ──────────────────────────────────────────────
 *
 * 대상(childId)은 항상 자녀이고, 호출자는 부모와 자녀 둘 다다. 그래서 안에 role 분기가
 * 있다. 부모 전용이었다면 필요 없었을 분기다.
 *
 *   부모 1이 GET /children/2/...  -> 자녀 2가 내 자녀인가 확인 -> 통과
 *   자녀 2가 GET /children/2/...  -> 토큰 id == 2 -> 통과 (DB를 보지 않는다)
 *   부모 9가 GET /children/2/...  -> 자녀 2의 부모는 1 -> 403
 *   자녀 3이 GET /children/2/...  -> 토큰 id != 2 -> 403 (DB를 보지 않는다)
 *
 * 마지막 줄을 빠뜨리면 절반만 막은 것이다. 자녀 3이 자녀 2의 데이터를 보는 것도
 * 타 가족 부모의 접근과 똑같은 사고다.
 *
 * 역할 애노테이션 @PreAuthorize("hasRole('PARENT')")로는 대체되지 않는다. 그것은
 * "부모인가"만 묻고 "누구의 부모인가"는 묻지 않는다. 이 호출 하나면 자녀 호출까지 커버한다.
 *
 * ────────────────────────────────────────────────────────────────────────
 *
 * 후속 4개 도메인이 주입받을 대상이므로 의존을 늘리지 않는다. S3Storage도 DTO도 넣지 않는다.
 */
@Service
public class FamilyAccessService {

    private final MemberMapper memberMapper;

    public FamilyAccessService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /**
     * 요청자가 childId 자녀의 데이터를 다뤄도 되는지 확인한다.
     *
     * 통과하면 아무것도 하지 않고, 아니면 AUTH_FORBIDDEN(403)을 던진다.
     * boolean을 돌려주지 않는 이유는 호출부가 if를 빠뜨리면 조용히 통과하기 때문이다.
     * 이 호출을 넘어왔다는 것 자체가 권한이 있다는 뜻이어야 한다.
     */
    @Transactional(readOnly = true)
    public void requireChildAccess(MemberPrincipal principal, Long childId) {
        if (principal == null || childId == null) {
            throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
        }

        // 자기 자신이면 관계를 볼 필요가 없다. 회원마다 role은 하나뿐이라
        // CHILD가 동시에 다른 자녀의 부모일 수 있는 경우는 없다.
        if ("CHILD".equals(principal.role())) {
            if (!childId.equals(principal.memberId())) {
                throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
            }
            return;
        }

        // 자녀의 ACTIVE 부모는 UQ_MBR_CONN_R_ACTIVE_CHILD 때문에 최대 1명이다.
        // 그래서 전용 EXISTS 쿼리 없이 조회 API가 쓰는 쿼리를 그대로 재사용한다.
        // 제약이 깨져 두 명이 되면 TooManyResultsException이 나면서 정당한 부모까지
        // 함께 막힌다. 조용히 통과하는 경로는 없다(fail-closed).
        if ("PARENT".equals(principal.role())) {
            MemberParentVO parent = memberMapper.selectActiveParentByChildId(childId);
            // parent가 null이면 아직 아무와도 연동되지 않은 자녀다. 403이 맞다.
            if (parent != null && principal.memberId().equals(parent.getParentId())) {
                return;
            }
        }

        throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
    }
}
