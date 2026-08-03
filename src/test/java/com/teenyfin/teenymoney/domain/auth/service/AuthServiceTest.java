package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.domain.auth.dto.request.LoginRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.SignupResponseDTO;
import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.auth.RefreshTokenStore;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private JwtProvider jwtProvider;
    private RefreshTokenStore refreshTokenStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        phoneVerificationService = mock(PhoneVerificationService.class);
        jwtProvider = mock(JwtProvider.class);
        refreshTokenStore = mock(RefreshTokenStore.class);
        authService = new AuthService(
                memberMapper,
                passwordEncoder,
                phoneVerificationService,
                jwtProvider,
                refreshTokenStore,
                CLOCK);
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
    void ageSixIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.signup(request(LocalDate.of(2019, 8, 4))));

        assertEquals(AuthErrorCode.AUTH_INCORRECT_AGE, exception.getErrorCode());
        verify(memberMapper, never()).insert(any(MemberVO.class));
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

    @Test
    void loginReturnsTokensAndMemberSummaryAndStoresRefreshToken() {
        MemberVO member = activeMember();
        when(memberMapper.selectByEmail("user@example.com")).thenReturn(member);
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(refreshTokenStore.getOrCreateGeneration(17L)).thenReturn("generation-17");
        when(jwtProvider.createAccessToken(17L, "PARENT", "generation-17"))
                .thenReturn("access-token");
        when(jwtProvider.createRefreshToken(17L, "generation-17"))
                .thenReturn("refresh-token");

        LoginResult result = authService.login(loginRequest(" USER@Example.COM ", "password123"));

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(17L, result.memberId());
        assertEquals("PARENT", result.role());
        assertEquals("Test User", result.name());
        verify(memberMapper).selectByEmail("user@example.com");
        verify(refreshTokenStore).save(17L, "refresh-token");
    }

    @Test
    void loginWithUnknownEmailReturnsInvalidCredentials() {
        when(memberMapper.selectByEmail("missing@example.com")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("missing@example.com", "password123")));

        assertEquals(AuthErrorCode.AUTH_INVALID_CREDENTIALS, exception.getErrorCode());
        verify(passwordEncoder).matches(anyString(), anyString());
        verify(jwtProvider, never()).createAccessToken(any(), any(), any());
        verify(refreshTokenStore, never()).save(any(), any());
    }

    @Test
    void loginWithWrongPasswordReturnsInvalidCredentials() {
        when(memberMapper.selectByEmail("user@example.com")).thenReturn(activeMember());
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "wrong-password")));

        assertEquals(AuthErrorCode.AUTH_INVALID_CREDENTIALS, exception.getErrorCode());
        verify(jwtProvider, never()).createAccessToken(any(), any(), any());
        verify(refreshTokenStore, never()).save(any(), any());
    }

    @Test
    void loginWithInactiveMemberReturnsInactiveMemberAfterPasswordMatches() {
        MemberVO member = activeMember();
        member.setStatus("INACTIVE");
        when(memberMapper.selectByEmail("user@example.com")).thenReturn(member);
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "password123")));

        assertEquals(AuthErrorCode.AUTH_INACTIVE_MEMBER, exception.getErrorCode());
        verify(jwtProvider, never()).createAccessToken(any(), any(), any());
        verify(refreshTokenStore, never()).save(any(), any());
    }

    @Test
    void loginDoesNotRevealInactiveMemberWhenPasswordIsWrong() {
        MemberVO member = activeMember();
        member.setStatus("INACTIVE");
        when(memberMapper.selectByEmail("user@example.com")).thenReturn(member);
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "wrong-password")));

        assertEquals(AuthErrorCode.AUTH_INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void loginTranslatesRedisFailureToServiceUnavailable() {
        when(memberMapper.selectByEmail("user@example.com")).thenReturn(activeMember());
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(refreshTokenStore.getOrCreateGeneration(17L)).thenReturn("generation-17");
        when(jwtProvider.createAccessToken(17L, "PARENT", "generation-17"))
                .thenReturn("access-token");
        when(jwtProvider.createRefreshToken(17L, "generation-17"))
                .thenReturn("refresh-token");
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(refreshTokenStore).save(17L, "refresh-token");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("user@example.com", "password123")));

        assertEquals(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void reissueRotatesRefreshAndReturnsNewAccessToken() {
        stubClaims("old-refresh", "17", JwtProvider.TOKEN_TYPE_REFRESH, "generation-17");
        when(refreshTokenStore.findGeneration(17L)).thenReturn("generation-17");
        when(memberMapper.selectById(17L)).thenReturn(activeMember());
        when(jwtProvider.createAccessToken(17L, "PARENT", "generation-17"))
                .thenReturn("new-access");
        when(jwtProvider.createRefreshToken(17L, "generation-17"))
                .thenReturn("new-refresh");
        when(refreshTokenStore.rotate(
                17L, "old-refresh", "new-refresh", "generation-17"))
                .thenReturn(true);

        TokenReissueResult result = authService.reissue("old-refresh");

        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
    }

    @Test
    void reissueRejectsMissingRefreshToken() {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> authService.reissue(null));

        assertEquals(AuthErrorCode.AUTH_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void reissueKeepsExpiredTokenDistinctFromInvalidToken() {
        when(jwtProvider.parse("expired-refresh"))
                .thenThrow(mock(ExpiredJwtException.class));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> authService.reissue("expired-refresh"));

        assertEquals(AuthErrorCode.AUTH_TOKEN_EXPIRED, exception.getErrorCode());
    }

    @Test
    void reissueRejectsAccessTokenAndMalformedToken() {
        stubClaims("access-token", "17", JwtProvider.TOKEN_TYPE_ACCESS, "generation-17");
        when(jwtProvider.parse("malformed-token"))
                .thenThrow(new MalformedJwtException("malformed"));

        BusinessException accessException = assertThrows(
                BusinessException.class, () -> authService.reissue("access-token"));
        BusinessException malformedException = assertThrows(
                BusinessException.class, () -> authService.reissue("malformed-token"));

        assertEquals(AuthErrorCode.AUTH_TOKEN_INVALID, accessException.getErrorCode());
        assertEquals(AuthErrorCode.AUTH_TOKEN_INVALID, malformedException.getErrorCode());
    }

    @Test
    void reissueRejectsGenerationOrRefreshTokenMismatch() {
        stubClaims("old-refresh", "17", JwtProvider.TOKEN_TYPE_REFRESH, "generation-17");
        when(refreshTokenStore.findGeneration(17L)).thenReturn("generation-17");
        when(memberMapper.selectById(17L)).thenReturn(activeMember());
        when(jwtProvider.createAccessToken(17L, "PARENT", "generation-17"))
                .thenReturn("new-access");
        when(jwtProvider.createRefreshToken(17L, "generation-17"))
                .thenReturn("new-refresh");
        when(refreshTokenStore.rotate(
                17L, "old-refresh", "new-refresh", "generation-17"))
                .thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> authService.reissue("old-refresh"));

        assertEquals(AuthErrorCode.AUTH_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void reissueRejectsInactiveMember() {
        MemberVO member = activeMember();
        member.setStatus("INACTIVE");
        stubClaims("old-refresh", "17", JwtProvider.TOKEN_TYPE_REFRESH, "generation-17");
        when(refreshTokenStore.findGeneration(17L)).thenReturn("generation-17");
        when(memberMapper.selectById(17L)).thenReturn(member);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> authService.reissue("old-refresh"));

        assertEquals(AuthErrorCode.AUTH_INACTIVE_MEMBER, exception.getErrorCode());
        verify(refreshTokenStore, never()).rotate(any(), any(), any(), any());
    }

    @Test
    void reissueTranslatesRedisFailureToServiceUnavailable() {
        stubClaims("old-refresh", "17", JwtProvider.TOKEN_TYPE_REFRESH, "generation-17");
        when(refreshTokenStore.findGeneration(17L))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> authService.reissue("old-refresh"));

        assertEquals(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void logoutRevokesOnlyAccessTokenAccount() {
        stubClaims("access-token", "17", JwtProvider.TOKEN_TYPE_ACCESS, "generation-17");
        when(refreshTokenStore.findGeneration(17L)).thenReturn("generation-17");

        authService.logout("access-token", null);

        verify(refreshTokenStore).revokeAll(17L);
        verify(refreshTokenStore, never()).revokeAll(18L);
    }

    @Test
    void logoutFallsBackToRefreshToken() {
        when(jwtProvider.parse("invalid-access"))
                .thenThrow(new MalformedJwtException("malformed"));
        stubClaims("refresh-token", "17", JwtProvider.TOKEN_TYPE_REFRESH, "generation-17");
        when(refreshTokenStore.findGeneration(17L)).thenReturn("generation-17");

        authService.logout("invalid-access", "refresh-token");

        verify(refreshTokenStore).revokeAll(17L);
    }

    @Test
    void logoutIsIdempotentWhenTokensOrGenerationAreMissing() {
        authService.logout(null, null);

        stubClaims("old-access", "17", JwtProvider.TOKEN_TYPE_ACCESS, "generation-17");
        when(refreshTokenStore.findGeneration(17L)).thenReturn(null);
        authService.logout("old-access", null);

        verify(refreshTokenStore, never()).revokeAll(any());
    }

    @Test
    void logoutTranslatesRedisFailureToServiceUnavailable() {
        stubClaims("access-token", "17", JwtProvider.TOKEN_TYPE_ACCESS, "generation-17");
        when(refreshTokenStore.findGeneration(17L))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.logout("access-token", null));

        assertEquals(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, exception.getErrorCode());
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

    private LoginRequestDTO loginRequest(String email, String password) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(email);
        request.setPassword(password);
        return request;
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
        member.setProfileImageUrl("https://example.com/profile.png");
        member.setStatus("ACTIVE");
        return member;
    }

    private Claims claims(String subject, String tokenType, String generation) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get(JwtProvider.CLAIM_TOKEN_TYPE, String.class)).thenReturn(tokenType);
        when(claims.get(JwtProvider.CLAIM_AUTH_GENERATION, String.class))
                .thenReturn(generation);
        return claims;
    }

    private void stubClaims(
            String token, String subject, String tokenType, String generation) {
        Claims claims = claims(subject, tokenType, generation);
        when(jwtProvider.parse(token)).thenReturn(claims);
    }
}
