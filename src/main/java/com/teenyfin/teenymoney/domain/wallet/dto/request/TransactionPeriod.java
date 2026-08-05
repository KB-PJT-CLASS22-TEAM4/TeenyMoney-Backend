package com.teenyfin.teenymoney.domain.wallet.dto.request;

import java.time.LocalDate;

public enum TransactionPeriod {


    // "1주일/1개월/3개월/6개월" 버튼 값.
    // 각 enum 상수가 "오늘 날짜를 주면 그 기간의 시작일을 계산해서 돌려주는 방법"을
    // 자기 자신 안에 직접 들고 있게 만들었다 (Java enum은 상수마다 메서드를 다르게 구현할 수 있음).
    // 이렇게 하면 Service 코드에 "WEEK면 1주 빼고, MONTH면 1달 빼고..." 같은 if/switch 분기가
    // 안 생기고, 그냥 period.startDateFrom(오늘)만 호출하면 끝난다.


    WEEK {
        @Override
        public LocalDate startDateFrom(LocalDate today) {
            return today.minusWeeks(1);
        }
    },
    MONTH {
        @Override
        public LocalDate startDateFrom(LocalDate today) {
            return today.minusMonths(1);
        }
    },
    THREE_MONTHS {
        @Override
        public LocalDate startDateFrom(LocalDate today) {
            return today.minusMonths(3);
        }
    },
    SIX_MONTHS {
        @Override
        public LocalDate startDateFrom(LocalDate today) {
            return today.minusMonths(6);
        }
    };


    // 각 enum 상수가 반드시 구현해야 하는 추상 메서드.
    public abstract LocalDate startDateFrom(LocalDate today);
}
