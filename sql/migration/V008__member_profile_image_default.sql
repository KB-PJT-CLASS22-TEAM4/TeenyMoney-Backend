-- 이슈 #86: 신규 회원 기본 프로필 이미지 key 적용
--
-- 가입 시 profile_image_key가 NULL로 저장되어 GET /me의 profileImageUrl이 null이었다.
-- 프로필을 설정하지 않은 회원이 대다수라, 화면마다 빈 값 분기를 들고 있어야 했다.
-- 컬럼 기본값으로 밀어 넣어 서버가 항상 유효한 URL을 내려주게 한다.
--
-- 기본값은 버킷 루트의 오브젝트 key다. URL이 아니다(V002 참고).
--   버킷 : teenymoney-media-kb22
--   key  : teenymoney_profile.png
-- 개인 이미지는 profile/{memberId}/{uuid}.{ext}에 쌓이므로 경로가 겹치지 않는다.
-- 나중에 profile/ 아래 고아 오브젝트를 라이프사이클 룰로 청소해도 기본 이미지는 남는다.

-- NOT NULL로 바꾸기 전에 기존 NULL을 채운다. 순서가 뒤집히면 ALTER가 실패한다.
-- (MySQL이 기존 NULL 행을 빈 문자열로 조용히 바꾸는 non-strict 모드라면 더 나쁘다.
--  presignedUrl()은 빈 문자열을 null로 취급하므로 그 행만 이미지가 안 나온다)
UPDATE T_MBR_INFO_M
SET profile_image_key = 'teenymoney_profile.png'
WHERE profile_image_key IS NULL;

-- DEFAULT를 컬럼에 두는 이유는 회원을 만드는 경로가 애플리케이션만이 아니기 때문이다.
-- seed 스크립트나 수동 INSERT도 같은 기본값을 받아야 한다.
ALTER TABLE T_MBR_INFO_M
    MODIFY COLUMN `profile_image_key` VARCHAR(1024) NOT NULL
        DEFAULT 'teenymoney_profile.png'
        COMMENT '프로필 이미지 S3 오브젝트 key (조회 시 presigned URL로 변환)';

-- 적용 후 확인: 0이어야 한다.
--   SELECT COUNT(*) FROM T_MBR_INFO_M WHERE profile_image_key IS NULL;
