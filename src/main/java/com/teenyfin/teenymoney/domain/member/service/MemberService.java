package com.teenyfin.teenymoney.domain.member.service;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    @Transactional(readOnly = true)
    public MemberMeResponseDTO getMe(Long memberId) {
        MemberVO member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException(AuthErrorCode.AUTH_INACTIVE_MEMBER);
        }
        return MemberMeResponseDTO.of(member);
    }
}
