package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.domain.member.vo.AgreementVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberChildVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemberMapper {

    MemberVO selectByEmail(@Param("email") String email);

    MemberVO selectById(@Param("id") Long id);

    MemberVO selectByIdForUpdate(@Param("id") Long id);

    boolean existsByEmail(@Param("email") String email);

    boolean existsByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    int insert(MemberVO member);

    int updateProfileImageKey(@Param("id") Long id,
                              @Param("profileImageKey") String profileImageKey);

    List<MemberChildVO> selectChildrenByParentId(@Param("parentId") Long parentId);

    MemberParentVO selectActiveParentByChildId(@Param("childId") Long childId);

    Long selectEffectiveAgreementId(
            // [보호자 가입 흐름 9] 요청한 약관 코드·버전이 현재 유효한지 조회한다.
            @Param("code") String code,
            @Param("version") String version,
            @Param("now") LocalDateTime now);

    // 약관 조회 API용. 목록은 전문(content)을 제외한다 - 약관 전문은 길어서
    // 목록 응답에 실으면 화면 하나가 수십 KB를 받는다.
    List<AgreementVO> selectEffectiveAgreements(@Param("now") LocalDateTime now);

    AgreementVO selectEffectiveAgreementByCode(@Param("code") String code,
                                               @Param("now") LocalDateTime now);

    int insertAgreementHistory(
            // [보호자 가입 흐름 14] 회원별 약관 동의 주체와 인증 근거를 이력으로 남긴다.
            @Param("memberId") Long memberId,
            @Param("agreementId") Long agreementId,
            @Param("status") String status,
            @Param("actorType") String actorType,
            @Param("actorMemberId") Long actorMemberId,
            @Param("verificationMethod") String verificationMethod,
            @Param("verificationReference") String verificationReference);

    int insertLegalGuardian(
            // [보호자 가입 흐름 13] 회원 계정이 없는 법정대리인의 인증 정보를 자녀에게 연결한다.
            @Param("childMemberId") Long childMemberId,
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("relationship") String relationship,
            @Param("verificationMethod") String verificationMethod,
            @Param("verificationReference") String verificationReference,
            @Param("verifiedAt") LocalDateTime verifiedAt);

    boolean existsActiveConnectionByChildId(@Param("childId") Long childId);

    // 부모·자녀의 역할 또는 ACTIVE 상태가 유효하지 않으면 0을 반환한다.
    int insertConnection(
            @Param("parentId") Long parentId,
            @Param("childId") Long childId
    );

    // customer_key가 지금도 null일 때만 갱신한다. WHERE 절의 "AND customer_key IS NULL"이
    // 동시성 가드 역할을 함 - 두 요청이 동시에 호출해도 DB가 한 번에 하나씩만 처리하니까,
    // 둘 다 성공할 수 없고 딱 하나만 실제로 값을 씀. 영향받은 row 수(0 또는 1)를 리턴해서
    // 호출한 쪽이 "내가 쓴 값이 채택됐는지" 알 수 있게 함.
    int updateCustomerKeyIfAbsent(@Param("id") Long id, @Param("customerKey") String customerKey);
}
