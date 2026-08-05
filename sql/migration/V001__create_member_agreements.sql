-- [보호자 가입 흐름 9] 가입 요청 버전의 유효성을 확인할 약관 원본 테이블
CREATE TABLE `T_MBR_AGRMT_M` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '약관 아이디',
    `code` VARCHAR(50) NOT NULL COMMENT '약관 코드',
    `version` VARCHAR(20) NOT NULL COMMENT '약관 버전',
    `title` VARCHAR(100) NOT NULL COMMENT '약관 제목',
    `content` TEXT NOT NULL COMMENT '약관 내용',
    `is_required` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '필수 동의 여부',
    `effective_at` DATETIME NOT NULL COMMENT '적용 시작 일시',
    `expired_at` DATETIME NULL COMMENT '적용 종료 일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `updated_at` DATETIME NULL COMMENT '수정 일시',
    CONSTRAINT `PK_MBR_AGRMT_M` PRIMARY KEY (`id`),
    CONSTRAINT `UQ_MBR_AGRMT_M_CODE_VERSION` UNIQUE (`code`, `version`)
);

-- [보호자 가입 흐름 14] 회원별 약관 동의 주체와 인증 근거 이력
CREATE TABLE `T_MBR_AGRMT_H` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 약관 동의 이력 아이디',
    `member_id` BIGINT NOT NULL COMMENT '동의 대상 회원 아이디',
    `agreement_id` BIGINT NOT NULL COMMENT '약관 아이디',
    `status` VARCHAR(20) NOT NULL COMMENT '동의 상태: AGREED/WITHDRAWN',
    `actor_type` VARCHAR(20) NOT NULL COMMENT '동의 수행자: SELF/LEGAL_GUARDIAN',
    `actor_member_id` BIGINT NULL COMMENT '동의 수행 회원 아이디',
    `verification_method` VARCHAR(20) NULL COMMENT '법정대리인 확인 방법: SMS/PASS 등',
    `verification_reference` VARCHAR(100) NULL COMMENT '본인확인 요청 식별값',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '동의 또는 철회 일시',
    CONSTRAINT `PK_MBR_AGRMT_H` PRIMARY KEY (`id`),
    CONSTRAINT `FK_T_MBR_INFO_M_TO_T_MBR_AGRMT_H_1`
        FOREIGN KEY (`member_id`) REFERENCES `T_MBR_INFO_M` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `FK_T_MBR_AGRMT_M_TO_T_MBR_AGRMT_H_1`
        FOREIGN KEY (`agreement_id`) REFERENCES `T_MBR_AGRMT_M` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `FK_T_MBR_INFO_M_TO_T_MBR_AGRMT_H_2`
        FOREIGN KEY (`actor_member_id`) REFERENCES `T_MBR_INFO_M` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `CK_MBR_AGRMT_H_STATUS`
        CHECK (`status` IN ('AGREED', 'WITHDRAWN')),
    CONSTRAINT `CK_MBR_AGRMT_H_ACTOR_TYPE`
        CHECK (`actor_type` IN ('SELF', 'LEGAL_GUARDIAN'))
);

CREATE INDEX `IX_MBR_AGRMT_H_N01`
    ON `T_MBR_AGRMT_H` (`member_id`, `agreement_id`, `created_at`);

-- [보호자 가입 흐름 13] 비회원 법정대리인 인증 정보를 자녀 회원과 연결
CREATE TABLE `T_MBR_LEGAL_GUARDIAN_M` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '비회원 법정대리인 정보 아이디',
    `child_member_id` BIGINT NOT NULL COMMENT '동의 대상 자녀 회원 아이디',
    `name` VARCHAR(50) NOT NULL COMMENT '법정대리인 이름',
    `phone_number` VARCHAR(20) NOT NULL COMMENT '인증한 휴대폰 번호',
    `relationship` VARCHAR(30) NOT NULL COMMENT '관계: FATHER/MOTHER/OTHER_LEGAL_GUARDIAN',
    `verification_method` VARCHAR(20) NOT NULL COMMENT '확인 방법: SMS_TEST/PASS 등',
    `verification_reference` VARCHAR(100) NOT NULL COMMENT '인증 요청 식별값',
    `verified_at` DATETIME NOT NULL COMMENT '인증 완료 일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    CONSTRAINT `PK_MBR_LEGAL_GUARDIAN_M` PRIMARY KEY (`id`),
    CONSTRAINT `UQ_MBR_LEGAL_GUARDIAN_M_VERIFICATION_REFERENCE`
        UNIQUE (`verification_reference`),
    CONSTRAINT `FK_T_MBR_INFO_M_TO_T_MBR_LEGAL_GUARDIAN_M_1`
        FOREIGN KEY (`child_member_id`) REFERENCES `T_MBR_INFO_M` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `CK_MBR_LEGAL_GUARDIAN_M_RELATIONSHIP`
        CHECK (`relationship` IN ('FATHER', 'MOTHER', 'OTHER_LEGAL_GUARDIAN'))
);

CREATE INDEX `IX_MBR_LEGAL_GUARDIAN_M_N01`
    ON `T_MBR_LEGAL_GUARDIAN_M` (`child_member_id`);

-- 운영 반영 전 법무 검토가 필요한 개발용 초안이다.
INSERT INTO `T_MBR_AGRMT_M` (
    `code`, `version`, `title`, `content`, `is_required`, `effective_at`
) VALUES
(
    'SERVICE_TERMS',
    '1.0',
    '서비스 이용약관',
    CONCAT(
        '[개발용 초안]', CHAR(10),
        '제1조(목적) 본 약관은 티니머니 서비스 이용에 필요한 기본 조건을 정합니다.', CHAR(10),
        '제2조(계정) 이용자는 정확한 정보를 제공하고 자신의 계정과 인증수단을 안전하게 관리해야 합니다.', CHAR(10),
        '제3조(이용자 의무) 이용자는 타인의 권리를 침해하거나 서비스를 부정한 목적으로 이용해서는 안 됩니다.', CHAR(10),
        '제4조(서비스 변경) 회사는 안정적인 운영을 위해 서비스의 일부를 변경하거나 중단할 수 있으며 중요한 변경은 사전에 알립니다.', CHAR(10),
        '제5조(책임) 회사와 이용자는 각자의 귀책사유로 발생한 손해에 대해 관련 법령에 따라 책임을 부담합니다.'
    ),
    TRUE,
    '2026-08-04 00:00:00'
),
(
    'PRIVACY',
    '1.0',
    '개인정보 수집·이용 동의',
    CONCAT(
        '[개발용 초안]', CHAR(10),
        '1. 수집 항목: 이름, 생년월일, 휴대폰 번호, 이메일, 비밀번호 해시, 약관 동의 이력', CHAR(10),
        '2. 이용 목적: 회원 식별과 인증, 계정 관리, 서비스 제공, 부정 이용 방지 및 고객 문의 처리', CHAR(10),
        '3. 보유 기간: 회원 탈퇴 시까지 보유하며, 관계 법령에 별도 보존 의무가 있는 경우 해당 기간 동안 보관합니다.', CHAR(10),
        '4. 동의 거부: 이용자는 개인정보 수집·이용 동의를 거부할 수 있으나 필수 정보 동의를 거부하면 회원가입이 제한됩니다.', CHAR(10),
        '5. 만 14세 미만 이용자는 법정대리인의 확인과 동의가 필요합니다.'
    ),
    TRUE,
    '2026-08-04 00:00:00'
);
