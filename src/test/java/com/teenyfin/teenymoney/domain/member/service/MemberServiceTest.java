package com.teenyfin.teenymoney.domain.member.service;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberProfileImageResponseDTO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.ErrorCode;
import com.teenyfin.teenymoney.global.storage.ImageFile;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("MemberService - 회원 조회와 프로필 이미지 변경")
class MemberServiceTest {

    private static final byte[] PNG =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private MemberMapper memberMapper;
    private S3Storage s3Storage;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        s3Storage = mock(S3Storage.class);
        memberService = new MemberService(memberMapper, s3Storage);
    }

    private MemberVO activeMember() {
        MemberVO member = new MemberVO();
        member.setId(17L);
        member.setRole("PARENT");
        member.setName("Test User");
        member.setBirthDate(LocalDate.of(1990, 1, 2));
        member.setPhoneNumber("01012345678");
        member.setEmail("user@example.com");
        member.setPassword("encoded-password");
        member.setProfileImageKey("profile/17/9f2c.png");
        member.setStatus("ACTIVE");
        return member;
    }

    private MultipartFile pngFile() {
        return new MockMultipartFile("file", "me.png", "image/png", PNG);
    }

    /** 거부를 기대하는 검증. 통과해버린 경우도 실제 결과로 출력한 뒤 실패시킨다. */
    private static void expectRejected(String situation, ErrorCode expected, ThrowingCallable call) {
        BusinessException thrown = catchThrowableOfType(call, BusinessException.class);

        String actual = (thrown == null)
                ? "거부되지 않고 정상 처리됨"
                : "거부됨 (" + thrown.getErrorCode().getCode() + ")";
        System.out.printf("    입력: %s%n    기대: 거부됨 (%s)%n    실제: %s%n%n",
                situation, expected.getCode(), actual);

        assertThat(thrown).as("거부되어야 하는데 통과했다: %s", situation).isNotNull();
        assertThat(thrown.getErrorCode())
                .as("거부되긴 했으나 사유가 다르다: %s", situation)
                .isEqualTo(expected);
    }

    // --- getMe ------------------------------------------------------------------

    @Test
    @DisplayName("프로필 key가 있는 회원 조회 -> 응답에 key가 아니라 서명된 URL이 담긴다")
    void getMeSignsStoredKey() {
        when(memberMapper.selectById(17L)).thenReturn(activeMember());
        when(s3Storage.presignedUrl("profile/17/9f2c.png"))
                .thenReturn("https://s3.example.com/profile/17/9f2c.png?X-Amz-Signature=sig");

        MemberMeResponseDTO response = memberService.getMe(17L);

        System.out.printf("    입력: DB의 profileImageKey = profile/17/9f2c.png%n"
                        + "    기대: 서명 URL (key 그대로가 아님)%n"
                        + "    실제: %s%n%n",
                response.getProfileImageUrl());

        assertThat(response.getProfileImageUrl())
                .isEqualTo("https://s3.example.com/profile/17/9f2c.png?X-Amz-Signature=sig");
        // key가 그대로 새어나가면 브라우저가 403을 받는다.
        assertThat(response.getProfileImageUrl()).isNotEqualTo("profile/17/9f2c.png");
    }

    @Test
    @DisplayName("프로필 이미지가 없는 회원 조회 -> 터지지 않고 profileImageUrl이 null")
    void getMeReturnsNullUrlWhenMemberHasNoProfileImage() {
        MemberVO member = activeMember();
        member.setProfileImageKey(null);
        when(memberMapper.selectById(17L)).thenReturn(member);
        when(s3Storage.presignedUrl(null)).thenReturn(null);

        MemberMeResponseDTO response = memberService.getMe(17L);

        System.out.printf("    입력: DB의 profileImageKey = null (대다수 회원)%n"
                        + "    기대: 예외 없이 profileImageUrl = null%n"
                        + "    실제: profileImageUrl = %s%n%n",
                response.getProfileImageUrl());

        assertThat(response.getProfileImageUrl()).isNull();
        assertThat(response.getMemberId()).isEqualTo(17L);
    }

    @Test
    @DisplayName("나머지 회원 정보 -> 그대로 응답에 전달된다")
    void getMePassesThroughMemberInformation() {
        when(memberMapper.selectById(17L)).thenReturn(activeMember());

        MemberMeResponseDTO response = memberService.getMe(17L);

        System.out.printf("    입력: memberId=17, PARENT, Test User%n"
                        + "    기대: 동일한 값이 응답에 담김%n"
                        + "    실제: memberId=%d, role=%s, name=%s, email=%s, birthDate=%s%n%n",
                response.getMemberId(), response.getRole(), response.getName(),
                response.getEmail(), response.getBirthDate());

        assertThat(response.getMemberId()).isEqualTo(17L);
        assertThat(response.getRole()).isEqualTo("PARENT");
        assertThat(response.getName()).isEqualTo("Test User");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
    }

    @Test
    @DisplayName("존재하지 않는 회원 조회 -> AUTH_TOKEN_INVALID로 거부")
    void getMeWithMissingMemberIsRejected() {
        when(memberMapper.selectById(17L)).thenReturn(null);

        expectRejected("selectById가 null (탈퇴했거나 없는 회원)",
                AuthErrorCode.AUTH_TOKEN_INVALID,
                () -> memberService.getMe(17L));
    }

    @Test
    @DisplayName("비활성 회원 조회 -> AUTH_INACTIVE_MEMBER로 거부")
    void getMeWithInactiveMemberIsRejected() {
        MemberVO member = activeMember();
        member.setStatus("INACTIVE");
        when(memberMapper.selectById(17L)).thenReturn(member);

        expectRejected("status = INACTIVE",
                AuthErrorCode.AUTH_INACTIVE_MEMBER,
                () -> memberService.getMe(17L));
    }

    // --- updateProfileImage -----------------------------------------------------

    @Test
    @DisplayName("정상 업로드 -> profile/{memberId} 아래 저장하고 key를 DB에 반영, 서명 URL 반환")
    void updateProfileImageUploadsUnderMemberPrefixAndStoresKey() {
        when(memberMapper.selectById(17L)).thenReturn(activeMember());
        when(s3Storage.upload(eq("profile/17"), any(ImageFile.class)))
                .thenReturn("profile/17/new.png");
        when(s3Storage.presignedUrl("profile/17/new.png"))
                .thenReturn("https://s3.example.com/signed");

        MemberProfileImageResponseDTO response =
                memberService.updateProfileImage(17L, pngFile());

        System.out.printf("    입력: memberId=17, me.png 업로드%n"
                        + "    기대: prefix=profile/17로 업로드, key를 DB에 저장, 서명 URL 반환%n"
                        + "    실제: DB 저장 key=profile/17/new.png, 응답=%s%n%n",
                response.getProfileImageUrl());

        verify(s3Storage).upload(eq("profile/17"), any(ImageFile.class));
        verify(memberMapper).updateProfileImageKey(17L, "profile/17/new.png");
        assertThat(response.getProfileImageUrl()).isEqualTo("https://s3.example.com/signed");
    }

    @Test
    @DisplayName("비활성 회원 업로드 -> S3를 건드리기 전에 AUTH_INACTIVE_MEMBER로 거부")
    void updateProfileImageRejectsInactiveMemberBeforeTouchingS3() {
        MemberVO inactive = activeMember();
        inactive.setStatus("INACTIVE");
        when(memberMapper.selectById(17L)).thenReturn(inactive);

        expectRejected("status = INACTIVE인 회원이 업로드 시도",
                AuthErrorCode.AUTH_INACTIVE_MEMBER,
                () -> memberService.updateProfileImage(17L, pngFile()));

        // 회원 확인이 업로드보다 먼저여야 한다. 순서가 뒤집히면 파일이 S3에 남고
        // 우리에겐 지울 권한(s3:DeleteObject)이 없다.
        System.out.printf("    추가 확인: S3 호출 여부%n"
                        + "    기대: 호출 없음 (회원 확인이 업로드보다 먼저)%n"
                        + "    실제: %s%n%n",
                Mockito.mockingDetails(s3Storage).getInvocations().isEmpty()
                        ? "호출 없음" : "호출됨 - 고아 객체가 남는다");
        verifyNoInteractions(s3Storage);
    }

    @Test
    @DisplayName("존재하지 않는 회원 업로드 -> S3를 건드리기 전에 AUTH_TOKEN_INVALID로 거부")
    void updateProfileImageRejectsUnknownMemberBeforeTouchingS3() {
        when(memberMapper.selectById(17L)).thenReturn(null);

        expectRejected("selectById가 null인 상태로 업로드 시도",
                AuthErrorCode.AUTH_TOKEN_INVALID,
                () -> memberService.updateProfileImage(17L, pngFile()));

        verifyNoInteractions(s3Storage);
    }

    @Test
    @DisplayName("위장 파일 업로드 -> ImageFile 검증에서 걸려 S3에 올라가지 않는다")
    void updateProfileImageRejectsDisguisedFileBeforeUploading() {
        when(memberMapper.selectById(17L)).thenReturn(activeMember());
        MultipartFile disguised = new MockMultipartFile(
                "file", "evil.png", "image/png", "#!/bin/sh\n".getBytes());

        expectRejected("evil.png / 내용은 셸 스크립트",
                com.teenyfin.teenymoney.global.storage.StorageErrorCode.STORAGE_UNSUPPORTED_TYPE,
                () -> memberService.updateProfileImage(17L, disguised));

        verifyNoInteractions(s3Storage);
    }
}
