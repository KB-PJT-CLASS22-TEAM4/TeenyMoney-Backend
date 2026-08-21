-- 기본 프로필 이미지를 role별로 나눈다.
--
-- V008은 role과 무관하게 'teenymoney_profile.png' 하나를 줬다. 이제 역할별 이미지를
-- 쓴다.
--   teenymoney_teen.png   (자녀)
--   teenymoney_parent.png (보호자)
-- teenymoney_profile.png은 버킷에 남아 있지만 더 이상 참조하지 않는다. 이 마이그레이션
-- 이후 그 key를 가진 행은 0이어야 하므로, 확인 후 오브젝트를 지워도 된다.
--
-- role은 컬럼 DEFAULT로 볼 수 없다(MySQL DEFAULT는 다른 컬럼을 참조하지 못한다).
-- 그래서 신규 회원은 MemberMapper.insert의 CASE가 채우고, 여기서는 기존 행만 고친다.

-- 아직 기본 이미지인 회원만 대상이다. 직접 올린 회원(profile/...)은 건드리지 않는다.
UPDATE T_MBR_INFO_M
SET profile_image_key = CASE WHEN role = 'PARENT'
                             THEN 'teenymoney_parent.png'
                             ELSE 'teenymoney_teen.png' END,
    updated_at = NOW()
WHERE profile_image_key = 'teenymoney_profile.png';

-- 컬럼 DEFAULT는 남겨둔다. seed 스크립트나 수동 INSERT처럼 애플리케이션을 거치지 않는
-- 경로가 여전히 있고, NOT NULL이라 값이 없으면 그쪽이 깨진다. role을 못 보므로 자녀
-- 이미지로 둔다. 은퇴시킨 key를 기본값으로 남겨두면 다시 새어 들어온다.
ALTER TABLE T_MBR_INFO_M
    MODIFY COLUMN `profile_image_key` VARCHAR(1024) NOT NULL
        DEFAULT 'teenymoney_teen.png'
        COMMENT '프로필 이미지 S3 오브젝트 key (조회 시 presigned URL로 변환)';

-- 적용 후 확인: 0이어야 한다.
--   SELECT COUNT(*) FROM T_MBR_INFO_M WHERE profile_image_key = 'teenymoney_profile.png';
