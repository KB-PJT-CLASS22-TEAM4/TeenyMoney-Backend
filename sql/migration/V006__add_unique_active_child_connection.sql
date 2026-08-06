-- ACTIVE 관계만 자녀별로 하나를 허용한다.
-- INACTIVE 행은 NULL이 되어 MySQL UNIQUE 제약의 중복 검사 대상에서 제외된다.
ALTER TABLE T_MBR_CONN_R
    ADD COLUMN active_child_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ACTIVE' THEN child_id ELSE NULL END
        ) VIRTUAL,
    ADD CONSTRAINT UQ_MBR_CONN_R_ACTIVE_CHILD
        UNIQUE (active_child_id);
