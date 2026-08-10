package com.teenyfin.teenymoney.domain.member.mapper;

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

    // 결제수단 등록(ChargeMethodService)에서 처음 UUID를 발급할 때 딱 한 번 씀.
    // 이미 customerKey가 있는 회원한테 다시 호출해도 그냥 덮어씀 - 호출하는 쪽(서비스)이
    // "null일 때만 호출"하도록 책임지는 구조라, 여기서 별도로 막지 않음.
    void updateCustomerKey(@Param("id") Long id, @Param("customerKey") String customerKey);
}
