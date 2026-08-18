package com.teenyfin.teenymoney.domain.member.service;

import com.teenyfin.teenymoney.domain.member.dto.response.AgreementResponseDTO;
import com.teenyfin.teenymoney.domain.member.exception.MemberErrorCode;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.AgreementVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 약관 조회. 읽기 전용이고 회원 정보를 다루지 않으므로 인증이 필요 없다
 * (SecurityConfig의 PUBLIC_ENDPOINTS에 등록되어 있다).
 *
 * "유효한 약관"의 기준 시각은 AuthService와 마찬가지로 Clock 빈에서 가져온다.
 * LocalDateTime.now()를 직접 부르면 테스트에서 시각을 고정할 수 없다.
 */
@Service
@Transactional(readOnly = true)
public class TermsService {

    private final MemberMapper memberMapper;
    private final Clock clock;

    public TermsService(MemberMapper memberMapper, Clock clock) {
        this.memberMapper = memberMapper;
        this.clock = clock;
    }

    /** 지금 시점에 유효한 약관 목록. 전문(content)은 포함하지 않는다. */
    public List<AgreementResponseDTO> getEffectiveTerms() {
        return memberMapper.selectEffectiveAgreements(LocalDateTime.now(clock)).stream()
                .map(AgreementResponseDTO::of)
                .collect(Collectors.toList());
    }

    /** 지금 시점에 유효한 해당 코드의 약관 전문. */
    public AgreementResponseDTO getEffectiveTerms(String code) {
        AgreementVO agreement =
                memberMapper.selectEffectiveAgreementByCode(code, LocalDateTime.now(clock));
        if (agreement == null) {
            throw new BusinessException(MemberErrorCode.AGREEMENT_NOT_FOUND);
        }
        return AgreementResponseDTO.of(agreement);
    }
}
