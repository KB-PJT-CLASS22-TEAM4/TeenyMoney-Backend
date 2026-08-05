package com.teenyfin.teenymoney.domain.member.service;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberProfileImageResponseDTO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.storage.ImageFile;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MemberService {

    private static final String PROFILE_KEY_PREFIX = "profile/";

    private final MemberMapper memberMapper;
    private final S3Storage s3Storage;

    public MemberService(MemberMapper memberMapper, S3Storage s3Storage) {
        this.memberMapper = memberMapper;
        this.s3Storage = s3Storage;
    }

    @Transactional(readOnly = true)
    public MemberMeResponseDTO getMe(Long memberId) {
        MemberVO member = loadActiveMember(memberId);
        // DB에 저장된 것은 key다. 서명하지 않고 내보내면 브라우저에서 403이 난다.
        return MemberMeResponseDTO.of(
                member, s3Storage.presignedUrl(member.getProfileImageKey()));
    }

    /**
     * 프로필 이미지를 교체한다.
     *
     * 업로드와 DB 저장을 한 트랜잭션으로 묶는다. 범용 업로드 엔드포인트를 따로 두면
     * 올렸는데 저장되지 않은 고아 객체가 생긴다.
     *
     * 이전 S3 객체는 지우지 않는다. 지우려면 IAM에 s3:DeleteObject를 열어야 하고,
     * 업로드는 됐는데 DB 갱신이 실패한 경우까지 생각해야 한다. 스토리지 비용이
     * 실제로 보일 때 라이프사이클 룰로 처리한다.
     */
    @Transactional
    public MemberProfileImageResponseDTO updateProfileImage(Long memberId, MultipartFile file) {
        // 회원 확인이 업로드보다 먼저다. 순서가 뒤집히면 탈퇴했거나 비활성인 회원이 올린
        // 파일이 S3에 남고, 우리에겐 그걸 지울 권한(s3:DeleteObject)이 없다.
        loadActiveMember(memberId);

        String key = s3Storage.upload(
                PROFILE_KEY_PREFIX + memberId, ImageFile.validate(file));
        memberMapper.updateProfileImageKey(memberId, key);

        return new MemberProfileImageResponseDTO(s3Storage.presignedUrl(key));
    }

    /** getMe와 updateProfileImage가 같은 확인을 하므로 한 곳에 둔다. */
    private MemberVO loadActiveMember(Long memberId) {
        MemberVO member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
        }
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException(AuthErrorCode.AUTH_INACTIVE_MEMBER);
        }
        return member;
    }
}
