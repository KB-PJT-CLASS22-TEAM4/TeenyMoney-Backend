-- 티니점수 변경 이력에 이벤트 코드와 이벤트 식별 키를 추가한다.
--
-- event_code:
-- 점수가 변경된 원인을 프로그램에서 구분하기 위한 코드다.
-- 예: DEPOSIT_MATURED, SAVING_FIXED_INSTALLMENT_PAID, LOAN_DEFAULTED
-- description은 화면 표시 문구이므로 변경될 수 있지만, event_code는 조회·통계·정책 판정에
-- 사용하는 고정된 식별값이다.
--
-- event_key:
-- 같은 결제·납입·만기·상환 이벤트가 재시도나 중복 호출로 여러 번 반영되는 것을 막기 위한 키다.
-- 예: SAVING_FIXED_PAID:10:3은 적금 가입 10번의 3회차 납입 이벤트를 의미한다.
-- 자녀별로 같은 event_key를 한 번만 저장할 수 있도록 아래에서 UNIQUE 제약을 추가한다.

-- 1. 기존 데이터가 있는 테이블이므로 우선 NULL을 허용한 상태로 새 컬럼을 추가한다.
-- 처음부터 NOT NULL로 추가하면 기존 이력에는 두 컬럼의 값이 없어서 컬럼 추가가 실패하거나
-- 기존 데이터와 제약 조건이 충돌할 수 있다.
ALTER TABLE `T_TNY_SCOREHIST_H`
    ADD COLUMN `event_code` VARCHAR(50) NULL
        COMMENT '티니점수 변경 원인을 구분하는 이벤트 코드'
        AFTER `score_after`,
    ADD COLUMN `event_key` VARCHAR(150) NULL
        COMMENT '동일 점수 이벤트의 중복 반영을 막는 자녀별 멱등성 키'
        AFTER `event_code`;

-- 2. 새 컬럼 추가 전에 저장된 기존 이력에는 event_code와 event_key가 없으므로 NULL이 들어 있다.
-- NULL이 남아 있으면 이후 컬럼을 NOT NULL로 변경할 수 없기 때문에 기존 행을 LEGACY 값으로 채운다.
--
-- 기존 데이터의 실제 이벤트 종류는 현재 정보만으로 정확하게 판단하기 어려우므로
-- event_code에는 과거 데이터라는 의미의 LEGACY를 저장한다.
--
-- event_key는 UNIQUE 제약과 충돌하지 않도록 기존 이력의 id를 붙여 행마다 다르게 만든다.
-- 예: id=1 → LEGACY:1, id=2 → LEGACY:2
UPDATE `T_TNY_SCOREHIST_H`
SET `event_code` = 'LEGACY',
    `event_key` = CONCAT('LEGACY:', `id`)
WHERE `event_code` IS NULL
   OR `event_key` IS NULL;

-- 3. 기존 데이터의 NULL을 모두 채웠으므로 두 컬럼을 NOT NULL로 변경한다.
-- 이제부터 새 티니점수 이력을 저장할 때 event_code와 event_key를 반드시 입력해야 한다.
--
-- 마지막으로 같은 자녀에게 같은 event_key가 두 번 저장되지 않도록 UNIQUE 제약을 추가한다.
-- 이를 통해 애플리케이션 재시도나 스케줄러 중복 실행이 발생해도 동일 이벤트의 점수가
-- 여러 번 반영되는 것을 DB 수준에서 방지한다.
--
-- 자녀가 다르면 같은 event_key를 사용할 수 있다.
-- 동일 자녀 + 동일 event_key 조합만 중복으로 거부한다.
ALTER TABLE `T_TNY_SCOREHIST_H`
    MODIFY COLUMN `event_code` VARCHAR(50) NOT NULL
    COMMENT '티니점수 변경 원인을 구분하는 이벤트 코드',
    MODIFY COLUMN `event_key` VARCHAR(150) NOT NULL
    COMMENT '동일 점수 이벤트의 중복 반영을 막는 자녀별 멱등성 키',
    ADD CONSTRAINT `UQ_TNY_SCOREHIST_H_CHILD_EVENT_KEY`
    UNIQUE (`child_id`, `event_key`);
