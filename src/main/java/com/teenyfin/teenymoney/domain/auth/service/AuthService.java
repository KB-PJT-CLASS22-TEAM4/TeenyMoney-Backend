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

        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtProvider.createRefreshToken(member.getId());
        try {
            refreshTokenStore.save(member.getId(), refreshToken);
        } catch (DataAccessException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        }

        return new LoginResult(
                accessToken,
                refreshToken,
                member.getId(),
                member.getRole(),
                member.getName());
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
        return age >= 7 && age <= 18 ? "CHILD" : "PARENT";
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.replaceAll("\\D", "");
    }
}
