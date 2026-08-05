package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MemberMapper {

    MemberVO selectByEmail(@Param("email") String email);

    MemberVO selectById(@Param("id") Long id);

    boolean existsByEmail(@Param("email") String email);

    boolean existsByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    int insert(MemberVO member);

    int updateProfileImageKey(@Param("id") Long id,
                              @Param("profileImageKey") String profileImageKey);
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
}
