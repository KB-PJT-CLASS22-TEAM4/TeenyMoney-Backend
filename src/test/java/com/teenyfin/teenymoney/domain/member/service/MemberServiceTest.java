package com.teenyfin.teenymoney.domain.member.service;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    private MemberMapper memberMapper;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        memberService = new MemberService(memberMapper);
    }

    @Test
    void getMeReturnsCurrentMemberInformation() {
        when(memberMapper.selectById(17L)).thenReturn(activeMember());

        MemberMeResponseDTO response = memberService.getMe(17L);

        assertEquals(17L, response.getMemberId());
        assertEquals("PARENT", response.getRole());
        assertEquals("Test User", response.getName());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("01012345678", response.getPhoneNumber());
        assertEquals(LocalDate.of(1990, 1, 2), response.getBirthDate());
        assertEquals("https://example.com/profile.png", response.getProfileImageUrl());
    }

    @Test
    void getMeWithMissingMemberReturnsInvalidToken() {
        when(memberMapper.selectById(17L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> memberService.getMe(17L));

        assertEquals(AuthErrorCode.AUTH_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void getMeWithInactiveMemberReturnsInactiveMember() {
        MemberVO member = activeMember();
        member.setStatus("INACTIVE");
        when(memberMapper.selectById(17L)).thenReturn(member);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> memberService.getMe(17L));

        assertEquals(AuthErrorCode.AUTH_INACTIVE_MEMBER, exception.getErrorCode());
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
}
