package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.domain.family.dto.response.FamilyLinkCodeResponseDTO;
import com.teenyfin.teenymoney.domain.family.exception.FamilyErrorCode;
import com.teenyfin.teenymoney.domain.family.store.FamilyLinkCodeStore;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

@Service
public class FamilyLinkCodeService {

    private static final int CODE_RANGE = 1_000_000;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final FamilyLinkCodeStore store;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public FamilyLinkCodeService(FamilyLinkCodeStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    // Controller에서 부모 ID 받아서 code 발급
    public FamilyLinkCodeResponseDTO makeCode(Long parentId) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = generateCode();

            if (!store.reserveCode(code, parentId, CODE_TTL)) {
                continue;
            }

            store.saveCurrentCode(parentId, code, CODE_TTL);

            return new FamilyLinkCodeResponseDTO(
                    code,
                    OffsetDateTime.now(clock).plus(CODE_TTL)
            );
        }

        throw new BusinessException(
                CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
        );
    }

    // 유효한 코드의 부모 조회
    public Long getParentIdByValidCode(String code) {
        Long parentId = store.findValidParentId(code);

        if (parentId == null) {
            throw new BusinessException(
                    FamilyErrorCode.FAMILY_LINK_CODE_INVALID
            );
        }

        return parentId;
    }

    private String generateCode() {
        return String.format(
                "%06d",
                secureRandom.nextInt(CODE_RANGE)
        );
    }
}