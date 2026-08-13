-- 알림(T_NTF_NOTI_L)에 제목(title) 컬럼을 추가하고, 유형(type) 컬럼은 제거한다.
-- type을 참조하는 CHECK 제약조건을 먼저 제거해야 컬럼을 삭제할 수 있다.
ALTER TABLE `T_NTF_NOTI_L`
    ADD COLUMN `title` VARCHAR(100) NULL
        COMMENT '알림 제목'
        AFTER `member_id`,
    DROP CHECK `CK_NTF_NOTI_L_TYPE`,
    DROP COLUMN `type`;