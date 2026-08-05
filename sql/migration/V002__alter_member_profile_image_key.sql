-- 이슈 #55: S3 비공개 버킷 도입에 따른 프로필 이미지 컬럼 의미 변경
--
-- 저장 값이 전체 URL에서 S3 오브젝트 key로 바뀐다.
--   변경 전: https://bucket.s3.ap-northeast-2.amazonaws.com/profile/17/9f2c.png
--   변경 후: profile/17/9f2c.png
--
-- 조회용 presigned URL은 만료되므로 저장할 수 있는 값이 아니고, 응답을 만들 때마다
-- key로부터 발급한다. 컬럼명을 함께 바꾸는 이유는, 값이 key인데 이름이 _url이면
-- 나중에 서명을 빼먹고 그대로 내보내도 코드가 정상 동작하고 브라우저에서 403이
-- 날 때까지 아무도 모르기 때문이다.
--
-- 적용 전 확인: 기존 행에 값이 없어야 변환 없이 rename할 수 있다.
--   SELECT COUNT(*) FROM T_MBR_INFO_M WHERE profile_image_url IS NOT NULL;
-- 결과가 0이 아니면 이 파일을 실행하지 말고 팀에 공유한다.
-- (기존 URL에서 key만 잘라내는 UPDATE가 먼저 필요하다)

-- RENAME COLUMN은 컬럼 정의를 건드리지 않아 CHANGE COLUMN보다 안전하다.
-- (CHANGE는 전체 정의를 다시 적어야 해서 타입이나 NULL 여부를 빠뜨리면 조용히 바뀐다)
-- MySQL 8.0 이상에서만 동작한다.
ALTER TABLE T_MBR_INFO_M
    RENAME COLUMN `profile_image_url` TO `profile_image_key`;

-- RENAME COLUMN은 COMMENT를 갱신하지 않는다. 이름만 바꾸면 DB 코멘트가
-- '프로필 이미지 URL'로 남아, 컬럼명을 바꾼 목적(값이 key인데 URL로 오해하는 것을 막는 것)이
-- DB를 직접 보는 사람에게는 그대로 무너진다.
ALTER TABLE T_MBR_INFO_M
    MODIFY COLUMN `profile_image_key` VARCHAR(1024) NULL
    COMMENT '프로필 이미지 S3 오브젝트 key (조회 시 presigned URL로 변환)';

ALTER TABLE T_MBR_INFO_M
    MODIFY COLUMN `profile_image_key` VARCHAR(1024) NULL
        COMMENT '프로필 이미지 S3 오브젝트 key (조회 시 presigned URL로 변환)';
