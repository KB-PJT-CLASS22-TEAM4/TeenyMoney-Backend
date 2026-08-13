-- V007 이후 마이그레이션이 등급 행을 참조하므로 로컬 시드보다 먼저 기준 데이터를 넣는다.
INSERT INTO `T_TNY_GRADE_A` (
    `grade_id`,
    `grade_name`,
    `min_score`,
    `max_score`,
    `bonus_rate`,
    `monthly_override_limit`,
    `color`
)
VALUES
    (1, '새싹',     0,  449, 0.00, 1, '#FF4D4D'),
    (2, '스타터', 450,  649, 1.00, 3, '#FF9F40'),
    (3, '플러스', 650,  749, 2.00, 4, '#FFD400'),
    (4, '프로',   750,  899, 3.00, 5, '#4CAF50'),
    (5, '마스터', 900, 1000, 5.00, 6, '#2196F3')
ON DUPLICATE KEY UPDATE
    `grade_name` = VALUES(`grade_name`),
    `min_score` = VALUES(`min_score`),
    `max_score` = VALUES(`max_score`),
    `bonus_rate` = VALUES(`bonus_rate`),
    `monthly_override_limit` = VALUES(`monthly_override_limit`),
    `color` = VALUES(`color`);
