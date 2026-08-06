package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.domain.family.dto.response.FamilyLinkCodeResponseDTO;
import com.teenyfin.teenymoney.domain.family.exception.FamilyErrorCode;
import com.teenyfin.teenymoney.domain.family.store.FamilyLinkCodeStore;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 가족 연동 코드 발급·소비.
 *
 * 계약: 부모 한 명에게 유효한 코드는 하나뿐이고, 재발급하면 직전 코드가 즉시 죽는다.
 *
 * 이 계약은 코드 키와 부모 키 두 개에 걸쳐 있어 원자적 갱신이 필요하다.
 * FamilyLinkCodeStore.tryIssueCode가 Lua로 그걸 보장하므로, 여기서는 호출 순서를
 * 신경 쓸 필요가 없다. 코드를 뽑아 넘기고 결과만 보면 된다.
 */
@Service
public class FamilyLinkCodeService {

    private final MemberMapper memberMapper;
    private static final int MAX_CONSUME_ATTEMPTS = 5;
    private static final Duration CONSUME_ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private static final int CODE_RANGE = 1_000_000;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    /**
     * 발급 남용 제한.
     *
     * 이건 빈도 제한일 뿐 멱등성 장치가 아니다. 응답이 이 시간보다 늦게 도착하면
     * 쿨다운이 만료된 뒤 재시도가 새 코드를 발급하고, 뒤늦게 도착한 첫 응답이
     * 이미 죽은 코드를 화면에 남긴다. 그 문제는 멱등 키가 막는다.
     */
    private static final Duration ISSUE_COOLDOWN = Duration.ofSeconds(5);

    /** UUID가 36자다. 넉넉히 잡되 Redis 키가 비대해지지 않을 선. */
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final FamilyLinkCodeStore store;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public FamilyLinkCodeService(FamilyLinkCodeStore store, MemberMapper memberMapper, Clock clock) {
        this.store = store;
        this.clock = clock;
        this.memberMapper = memberMapper;
    }

    /**
     * 부모에게 새 코드를 발급한다. 직전 코드는 이 시점에 무효가 된다.
     *
     * idempotencyKey는 '한 번의 발급 의도'를 식별한다. 같은 키로 다시 오면 새 코드를
     * 만들지 않고 그때 발급한 코드를 그대로 돌려준다. 그래야 타임아웃 재시도의 늦은 응답이
     * 화면에 죽은 코드를 남기지 않는다.
     *
     * 키는 필수다. 서버가 대신 만들어주면 요청마다 다른 의도가 되어 중복 제거가 성립하지 않고,
     * 보장이 없는 채로 조용히 동작한다. 없으면 차라리 거절하는 편이 낫다.
     */
    public FamilyLinkCodeResponseDTO makeCode(Long parentId, String idempotencyKey) {
        String key = requireValidIdempotencyKey(idempotencyKey);

        // 재시도가 쿨다운에 걸려 429를 받으면 안 된다. 멱등 확인이 먼저다.
        String alreadyIssued = store.findIssuedCode(parentId, key);
        if (alreadyIssued != null) {
            return response(alreadyIssued);
        }

        if (!store.tryStartCooldown(parentId, ISSUE_COOLDOWN)) {
            throw new BusinessException(
                    FamilyErrorCode.FAMILY_LINK_CODE_TOO_SOON
            );
        }

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateCode();

            // 스크립트가 멱등 키를 다시 확인하므로, 위 조회와 여기 사이에 낀 동시 요청도 안전하다.
            String issued = store.tryIssueCode(parentId, candidate, key, CODE_TTL);
            if (issued != null) {
                return response(issued);
            }
            // 다른 부모가 쓰는 코드다. 이전 코드는 아직 살아 있으므로 다시 뽑기만 하면 된다.
        }

        // 10회 연속 충돌. 코드 공간(10^6)이 포화됐다는 뜻이라 재시도로는 못 푼다.
        throw new BusinessException(
                CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
        );
    }

    /**
     * 값이 비었거나 지나치게 긴 키를 거른다.
     *
     * 헤더 자체가 없는 경우는 @RequestHeader(required = true)가 먼저 걸러 400을 낸다.
     * 여기서 막는 건 '헤더는 왔는데 값이 쓸모없는' 경우다.
     *
     * 길이 상한을 두는 이유: 이 값이 Redis 키 이름의 일부가 된다. 헤더 크기는 톰캣이
     * 8KB까지 허용하므로, 상한이 없으면 수 KB짜리 키가 만들어진다. UUID는 36자다.
     */
    private String requireValidIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(
                    FamilyErrorCode.FAMILY_IDEMPOTENCY_KEY_INVALID
            );
        }

        return idempotencyKey;
    }

    /**
     * 만료 시각은 Redis에 남은 TTL로 계산한다.
     * 멱등 재사용으로 받은 코드는 발급 시각이 과거라, 지금+10분으로 계산하면 실제보다 늦다.
     */
    private FamilyLinkCodeResponseDTO response(String code) {
        return new FamilyLinkCodeResponseDTO(
                code,
                OffsetDateTime.now(clock).plus(store.remainingTtl(code))
        );
    }

    /**
     * 코드를 소비하고 부모 ID를 돌려준다. 한 번 쓰인 코드는 즉시 사라진다.
     *
     * 시도 횟수 제한은 여기가 아니라 이 메서드를 호출할 연동 API에서 건다.
     * 6자리 코드는 제한이 없으면 전수 탐색이 가능하다.
     */
    public Long consumeCode(String code) {
        Long parentId = store.consumeCode(code);

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

    @Transactional
    public void linkChild(Long childId, String code) {
        if (memberMapper.existsActiveConnectionByChildId(childId)) {
            throw new BusinessException(
                    FamilyErrorCode.FAMILY_ALREADY_LINKED
            );
        }

        Long attempts = store.incrementConsumeAttempts(
                childId,
                CONSUME_ATTEMPT_WINDOW
        );

        if (attempts == null) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
            );
        }

        if (attempts > MAX_CONSUME_ATTEMPTS) {
            throw new BusinessException(
                    FamilyErrorCode.FAMILY_LINK_TOO_MANY_ATTEMPTS
            );
        }

        Long parentId = consumeCode(code);

        try {
            int inserted = memberMapper.insertConnection(
                    parentId,
                    childId
            );

            if (inserted != 1) {
                throw new BusinessException(
                        FamilyErrorCode.FAMILY_LINK_CODE_INVALID
                );
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    FamilyErrorCode.FAMILY_ALREADY_LINKED
            );
        }
    }

}
