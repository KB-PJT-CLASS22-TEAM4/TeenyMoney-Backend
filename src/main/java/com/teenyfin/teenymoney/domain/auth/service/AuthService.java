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
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final PhoneVerificationService phoneVerificationService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    public AuthService(
            MemberMapper memberMapper,
            PasswordEncoder passwordEncoder,
            PhoneVerificationService phoneVerificationService,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            Clock clock) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.phoneVerificationService = phoneVerificationService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.clock = clock;
    }

    @Transactional
    public SignupResponseDTO signup(SignupRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        LocalDate birthDate = request.getBirthDate();
        validateBirthDate(birthDate);

        phoneVerificationService.verify(phoneNumber, request.getVerificationCode());

        if (memberMapper.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.AUTH_DUPLICATE_EMAIL);
        }
        if (memberMapper.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(AuthErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
        }

        MemberVO member = new MemberVO();
        member.setRole(deriveRole(birthDate));
        member.setName(request.getName().trim());
        member.setBirthDate(birthDate);
        member.setPhoneNumber(phoneNumber);
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(request.getPassword()));

        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException exception) {
            throw translateDuplicate(email, phoneNumber, exception);
        }

        phoneVerificationService.consume(phoneNumber);
        return SignupResponseDTO.of(member.getId());
    }

    public boolean isEmailAvailable(String rawEmail) {
        return !memberMapper.existsByEmail(normalizeEmail(rawEmail));
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequestDTO request) {
        MemberVO member = memberMapper.selectByEmail(normalizeEmail(request.getEmail()));
        String encodedPassword = member == null
                ? DUMMY_PASSWORD_HASH
                : member.getPassword();
        boolean passwordMatches = passwordEncoder.matches(
                request.getPassword(), encodedPassword);

        if (member == null || !passwordMatches) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException(AuthErrorCode.AUTH_INACTIVE_MEMBER);
        }

        try {
            String generation = refreshTokenStore.getOrCreateGeneration(member.getId());
            if (generation == null) {
                throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
            }
            String accessToken = jwtProvider.createAccessToken(
                    member.getId(), member.getRole(), generation);
            String refreshToken = jwtProvider.createRefreshToken(member.getId(), generation);
            refreshTokenStore.save(member.getId(), refreshToken);

            return new LoginResult(
                    accessToken,
                    refreshToken,
                    member.getId(),
                    member.getRole(),
                    member.getName());
        } catch (DataAccessException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public TokenReissueResult reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }

        Claims claims;
        try {
            claims = jwtProvider.parse(refreshToken);
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }

        if (!JwtProvider.TOKEN_TYPE_REFRESH.equals(
                claims.get(JwtProvider.CLAIM_TOKEN_TYPE, String.class))) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }

        Long memberId;
        try {
            memberId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }
        String generation = claims.get(JwtProvider.CLAIM_AUTH_GENERATION, String.class);
        if (generation == null || generation.isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }

        try {
            if (!generation.equals(refreshTokenStore.findGeneration(memberId))) {
                throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
            }

            MemberVO member = memberMapper.selectById(memberId);
            if (member == null) {
                throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
            }
            if (!"ACTIVE".equals(member.getStatus())) {
                throw new BusinessException(AuthErrorCode.AUTH_INACTIVE_MEMBER);
            }

            String accessToken = jwtProvider.createAccessToken(
                    memberId, member.getRole(), generation);
            String newRefreshToken = jwtProvider.createRefreshToken(memberId, generation);
            if (!refreshTokenStore.rotate(
                    memberId, refreshToken, newRefreshToken, generation)) {
                throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
            }
            return new TokenReissueResult(accessToken, newRefreshToken);
        } catch (DataAccessException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        }
    }

    public void logout(String accessToken, String refreshToken) {
        Claims claims = parseLogoutToken(accessToken, JwtProvider.TOKEN_TYPE_ACCESS);
        if (claims == null) {
            claims = parseLogoutToken(refreshToken, JwtProvider.TOKEN_TYPE_REFRESH);
        }
        if (claims == null) {
            return;
        }

        try {
            Long memberId = Long.valueOf(claims.getSubject());
            String generation = claims.get(
                    JwtProvider.CLAIM_AUTH_GENERATION, String.class);
            if (generation != null && generation.equals(
                    refreshTokenStore.findGeneration(memberId))) {
                refreshTokenStore.revokeAll(memberId);
            }
        } catch (NumberFormatException ignored) {
            // Invalid tokens make logout a no-op.
        } catch (DataAccessException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        }
    }

    private Claims parseLogoutToken(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = jwtProvider.parse(token);
            return expectedType.equals(
                    claims.get(JwtProvider.CLAIM_TOKEN_TYPE, String.class))
                    ? claims
                    : null;
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    private RuntimeException translateDuplicate(
            String email,
            String phoneNumber,
            DuplicateKeyException original) {
        if (memberMapper.existsByEmail(email)) {
            return new BusinessException(AuthErrorCode.AUTH_DUPLICATE_EMAIL);
        }
        if (memberMapper.existsByPhoneNumber(phoneNumber)) {
            return new BusinessException(AuthErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
        }
        return original;
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null || !birthDate.isBefore(LocalDate.now(clock))) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private String deriveRole(LocalDate birthDate) {
        int age = Period.between(birthDate, LocalDate.now(clock)).getYears();
        if (age < 7) {
            throw new BusinessException(AuthErrorCode.AUTH_INCORRECT_AGE);
        }
        return age <= 18 ? "CHILD" : "PARENT";
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.replaceAll("\\D", "");
    }
}
