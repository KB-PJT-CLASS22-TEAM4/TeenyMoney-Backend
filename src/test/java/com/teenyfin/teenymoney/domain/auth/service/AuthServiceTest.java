package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.SignupResponseDTO;
import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private MemberMapper memberMapper;
    private PasswordEncoder passwordEncoder;
    private PhoneVerificationService phoneVerificationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        phoneVerificationService = mock(PhoneVerificationService.class);
        authService = new AuthService(
                memberMapper, passwordEncoder, phoneVerificationService, CLOCK);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
    }

    @Test
    void signupNormalizesInputHashesPasswordAndConsumesCodeAfterInsert() {
        SignupRequestDTO request = request(LocalDate.of(2010, 1, 2));
        request.setEmail("  USER@Example.COM ");
        request.setPhoneNumber("010-1234-5678");
        doAnswer(invocation -> {
            MemberVO member = invocation.getArgument(0);
            member.setId(17L);
            return 1;
        }).when(memberMapper).insert(any(MemberVO.class));

        SignupResponseDTO response = authService.signup(request);

        ArgumentCaptor<MemberVO> memberCaptor = ArgumentCaptor.forClass(MemberVO.class);
        verify(memberMapper).insert(memberCaptor.capture());
        MemberVO inserted = memberCaptor.getValue();
        assertEquals("user@example.com", inserted.getEmail());
        assertEquals("01012345678", inserted.getPhoneNumber());
        assertEquals("encoded-password", inserted.getPassword());
        assertEquals("CHILD", inserted.getRole());
        assertEquals(17L, response.getMemberId());

        InOrder order = inOrder(memberMapper, phoneVerificationService);
        order.verify(phoneVerificationService).verify("01012345678", "123456");
        order.verify(memberMapper).insert(any(MemberVO.class));
        order.verify(phoneVerificationService).consume("01012345678");
    }

    @Test
    void exactAgeSevenIsChild() {
        assertEquals("CHILD", signupAndCaptureRole(LocalDate.of(2019, 8, 3)));
    }

    @Test
    void exactAgeEighteenIsChild() {
        assertEquals("CHILD", signupAndCaptureRole(LocalDate.of(2008, 8, 3)));
    }

    @Test
    void ageSixIsParent() {
        assertEquals("PARENT", signupAndCaptureRole(LocalDate.of(2019, 8, 4)));
    }

    @Test
    void ageNineteenIsParent() {
        assertEquals("PARENT", signupAndCaptureRole(LocalDate.of(2007, 8, 3)));
    }

    @Test
    void todayBirthDateIsInvalidInput() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.signup(request(LocalDate.of(2026, 8, 3))));

        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, exception.getErrorCode());
        verify(memberMapper, never()).insert(any(MemberVO.class));
    }

    @Test
    void futureBirthDateIsInvalidInput() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.signup(request(LocalDate.of(2026, 8, 4))));

        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void phoneVerificationRunsBeforeDuplicateEmailCheck() {
        when(memberMapper.existsByEmail("user@example.com")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.signup(request(LocalDate.of(2010, 1, 2))));

        assertEquals(AuthErrorCode.AUTH_DUPLICATE_EMAIL, exception.getErrorCode());
        InOrder order = inOrder(phoneVerificationService, memberMapper);
        order.verify(phoneVerificationService).verify("01012345678", "123456");
        order.verify(memberMapper).existsByEmail("user@example.com");
    }

    @Test
    void phoneVerificationRunsBeforeDuplicatePhoneCheck() {
        when(memberMapper.existsByPhoneNumber("01012345678")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.signup(request(LocalDate.of(2010, 1, 2))));

        assertEquals(AuthErrorCode.AUTH_DUPLICATE_PHONE_NUMBER, exception.getErrorCode());
        InOrder order = inOrder(phoneVerificationService, memberMapper);
        order.verify(phoneVerificationService).verify("01012345678", "123456");
        order.verify(memberMapper).existsByEmail("user@example.com");
        order.verify(memberMapper).existsByPhoneNumber("01012345678");
    }

    @Test
    void insertFailureDoesNotConsumeVerificationState() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(memberMapper).insert(any(MemberVO.class));

        assertThrows(IllegalStateException.class,
                () -> authService.signup(request(LocalDate.of(2010, 1, 2))));

        verify(phoneVerificationService, never()).consume(any());
    }

    @Test
    void duplicateKeyRaceIsTranslatedToEmailConflict() {
        when(memberMapper.existsByEmail("user@example.com")).thenReturn(false, true);
        doThrow(new DuplicateKeyException("email unique constraint"))
                .when(memberMapper).insert(any(MemberVO.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.signup(request(LocalDate.of(2010, 1, 2))));

        assertEquals(AuthErrorCode.AUTH_DUPLICATE_EMAIL, exception.getErrorCode());
        verify(phoneVerificationService, never()).consume(any());
    }

    @Test
    void emailAvailabilityUsesNormalizedEmail() {
        when(memberMapper.existsByEmail("user@example.com")).thenReturn(false);

        assertEquals(true, authService.isEmailAvailable(" USER@Example.COM "));
    }

    private String signupAndCaptureRole(LocalDate birthDate) {
        doAnswer(invocation -> {
            MemberVO member = invocation.getArgument(0);
            member.setId(17L);
            return 1;
        }).when(memberMapper).insert(any(MemberVO.class));
        authService.signup(request(birthDate));

        ArgumentCaptor<MemberVO> captor = ArgumentCaptor.forClass(MemberVO.class);
        verify(memberMapper).insert(captor.capture());
        return captor.getValue().getRole();
    }

    private SignupRequestDTO request(LocalDate birthDate) {
        SignupRequestDTO request = new SignupRequestDTO();
        request.setName("홍길동");
        request.setBirthDate(birthDate);
        request.setPhoneNumber("01012345678");
        request.setVerificationCode("123456");
        request.setEmail("user@example.com");
        request.setPassword("password123");
        return request;
    }
}
