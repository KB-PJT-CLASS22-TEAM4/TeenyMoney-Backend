package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    MemberVO selectByEmail(@Param("email") String email);

    MemberVO selectById(@Param("id") Long id);

    boolean existsByEmail(@Param("email") String email);

    boolean existsByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    int insert(MemberVO member);

    int updateProfileImageKey(@Param("id") Long id,
                              @Param("profileImageKey") String profileImageKey);
}
