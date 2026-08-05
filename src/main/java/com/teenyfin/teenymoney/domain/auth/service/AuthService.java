package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.domain.auth.dto.request.LoginRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.SignupResponseDTO;
import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.auth.RefreshTokenStore;
import com.teenyfin.teenymoney.global.auth.LegalGuardianConsentStore;
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
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Locale;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String SERVICE_TERMS = "SERVICE_TERMS";
    private static final String PRIVACY_TERMS = "PRIVACY";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final PhoneVerificationService phoneVerificationService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final LegalGuardianConsentStore legalGuardianConsentStore;
    private final Clock clock;

    public AuthService(
            MemberMapper memberMapper,
            PasswordEncoder passwordEncoder,
            PhoneVerificationService phoneVerificationService,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            LegalGuardianConsentStore legalGuardianConsentStore,
            Clock clock) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.phoneVerificationService = phoneVerificationService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.legalGuardianConsentStore = legalGuardianConsentStore;
        this.clock = clock;
    }

    @Transactional
    public SignupResponseDTO signup(SignupRequestDTO request) {
        // [보호자 가입 흐름 9] 가입 입력값을 정규화하고 나이·비밀번호·현재 약관 버전을 먼저 검증한다.
        String email = normalizeEmail(request.getEmail());
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        LocalDate birthDate = request.getBirthDate();

        validateBirthDate(birthDate);
        String role = deriveRole(birthDate);

        validatePassword(request.getPassword(), request.getPasswordConfirm(), email, phoneNumber);

        Long serviceAgreementId = requireEffectiveAgreement(SERVICE_TERMS, request.getServiceTermsVersion());
        Long privacyAgreementId = requireEffectiveAgreement(PRIVACY_TERMS, request.getPrivacyTermsVersion());

        // [보호자 가입 흐름 10] 만 14세 미만이면 Redis의 보호자 동의 토큰과 약관 버전을 검증한다.
        LegalGuardianConsent legalGuardianConsent = requireLegalGuardianConsent(request, birthDate);

        // [보호자 가입 흐름 11] 가입자 본인의 SMS 인증번호와 이메일·휴대폰 중복을 확인한다.
        phoneVerificationService.verify(phoneNumber, request.getVerificationCode());

        if (memberMapper.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.AUTH_DUPLICATE_EMAIL);
        }
        if (memberMapper.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(AuthErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
        }

        MemberVO member = new MemberVO();
        member.setRole(role);
        member.setName(request.getName().trim());
        member.setBirthDate(birthDate);
        member.setPhoneNumber(phoneNumber);
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(request.getPassword()));

        try {
            // [보호자 가입 흐름 12] 회원을 먼저 저장해 보호자·약관 이력에서 사용할 회원 ID를 생성한다.
            memberMapper.insert(member);
            if (legalGuardianConsent != null) {
                // [보호자 가입 흐름 13] 만 14세 미만 가입자의 비회원 법정대리인 인증 정보를 저장한다.
                memberMapper.insertLegalGuardian(
                        member.getId(),
                        legalGuardianConsent.name(),
                        legalGuardianConsent.phoneNumber(),
                        legalGuardianConsent.relationship(),
                        legalGuardianConsent.verificationMethod(),
                        legalGuardianConsent.verificationReference(),
                        legalGuardianConsent.verifiedAt());
            }
            String actorType = legalGuardianConsent == null ? "SELF" : "LEGAL_GUARDIAN";
            Long actorMemberId = legalGuardianConsent == null ? member.getId() : null;
            String verificationMethod = legalGuardianConsent == null
                    ? null : legalGuardianConsent.verificationMethod();
            String verificationReference = legalGuardianConsent == null
                    ? null : legalGuardianConsent.verificationReference();
            // [보호자 가입 흐름 14] 서비스·개인정보 동의 이력을 SELF 또는 LEGAL_GUARDIAN 수행자로 저장한다.
            memberMapper.insertAgreementHistory(
                    member.getId(), serviceAgreementId, "AGREED", actorType,
                    actorMemberId, verificationMethod, verificationReference);
            memberMapper.insertAgreementHistory(
                    member.getId(), privacyAgreementId, "AGREED", actorType,
                    actorMemberId, verificationMethod, verificationReference);
        } catch (DuplicateKeyException exception) {
            throw translateDuplicate(email, phoneNumber, exception);
        }

        // [보호자 가입 흐름 15] 가입이 모두 성공한 경우에만 가입자 인증번호와 보호자 토큰을 소비한다.
        phoneVerificationService.consume(phoneNumber);
        if (legalGuardianConsent != null) {
            legalGuardianConsentStore.delete(request.getLegalGuardianConsentToken());
        }
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
        if (birthDate == null || birthDate.isAfter(LocalDate.now(clock))) {
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

    private void validatePassword(
            String password,
            String passwordConfirm,
            String email,
            String phoneNumber) {

        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(AuthErrorCode.AUTH_PASSWORD_MISMATCH);
        }

        String lowerPassword = password.toLowerCase(Locale.ROOT);

        if (lowerPassword.contains(email)
                || password.replace("-", "").contains(phoneNumber)) {
            throw new BusinessException(AuthErrorCode.AUTH_PASSWORD_CONTAINS_PERSONAL_INFO);
        }
    }

    private Long requireEffectiveAgreement(String code, String version) {
        // 요청 버전이 현재 적용 중인 약관인지 DB 기준으로 확인한다.
        Long agreementId = memberMapper.selectEffectiveAgreementId(
                code,
                version,
                LocalDateTime.now(clock)
        );

        if (agreementId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_AGREEMENT_VERSION);
        }

        return agreementId;
    }

    private LegalGuardianConsent requireLegalGuardianConsent(
            SignupRequestDTO request, LocalDate birthDate) {
        // 만 14세가 되는 생일이 지났거나 오늘이면 보호자 동의 대상이 아니다.
        if (!birthDate.plusYears(14).isAfter(LocalDate.now(clock))) {
            return null;
        }
        if (request.getLegalGuardianConsentToken() == null
                || request.getLegalGuardianConsentToken().isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_LEGAL_GUARDIAN_CONSENT_REQUIRED);
        }
        LegalGuardianConsent consent = legalGuardianConsentStore.find(
                request.getLegalGuardianConsentToken());
        // 발급 이후 약관 버전이 달라졌다면 이전 동의를 현재 가입에 사용할 수 없다.
        if (consent == null
                || !request.getServiceTermsVersion().equals(consent.serviceTermsVersion())
                || !request.getPrivacyTermsVersion().equals(consent.privacyTermsVersion())) {
            throw new BusinessException(AuthErrorCode.AUTH_LEGAL_GUARDIAN_CONSENT_INVALID);
        }
        return consent;
    }
}
