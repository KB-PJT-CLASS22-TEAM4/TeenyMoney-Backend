# 머니 리포트 API — 프론트 연동 안내

이슈 #129. 1차 구현 범위입니다. `summary`(한눈에 보기) / `insights`(금융 습관) /
`teenyScore`(점수 변화)는 2차에서 같은 응답에 **키가 추가**됩니다. 지금은 키 자체가 없습니다.

Swagger: `/swagger-ui.html` → `Report`

## 엔드포인트

```http
GET /api/v1/reports/money/children/{childId}?month=2026-08
Authorization: Bearer {accessToken}
```

| | |
| --- | --- |
| `childId` | 대상 자녀의 `memberId` |
| `month` | `yyyy-MM`. **생략하면 현재 월.** 날짜는 모두 `Asia/Seoul` 기준 |

**자녀 본인과 그 자녀의 부모가 같은 엔드포인트를 씁니다.**

- 자녀 화면: 로그인한 자신의 `memberId`를 넣습니다 (`authStore.memberId`)
- 부모 화면: 자녀 관리 화면의 `route.params.childId`를 그대로 넣습니다
  (티니점수·금융상품이 이미 쓰는 방식과 같습니다)

남의 자녀를 지목하면 403입니다.

## 응답

`ApiResponse` 껍데기는 다른 API와 같습니다. 아래는 `data` 안쪽입니다.

```json
{
  "period": {
    "yearMonth": "2026-07",
    "startDate": "2026-07-01",
    "endDate": "2026-07-31",
    "status": "COMPLETED",
    "comparisonStartDate": "2026-06-01",
    "comparisonEndDate": "2026-06-30"
  },
  "audience": { "ageBand": "TEEN" },
  "availableMonths": [
    { "yearMonth": "2026-08", "status": "IN_PROGRESS" },
    { "yearMonth": "2026-07", "status": "COMPLETED" },
    { "yearMonth": "2026-06", "status": "COMPLETED" }
  ],
  "spending": {
    "totalAmount": 52000,
    "paymentCount": 4,
    "comparisonAmount": 40000,
    "comparisonCount": 3,
    "comparisonAmountDelta": 12000,
    "comparisonCountDelta": 1,
    "weeklyTrend": [
      { "weekNo": 1, "startDate": "2026-07-01", "endDate": "2026-07-05", "amount": 0,     "paymentCount": 0 },
      { "weekNo": 2, "startDate": "2026-07-06", "endDate": "2026-07-12", "amount": 27000, "paymentCount": 2 },
      { "weekNo": 3, "startDate": "2026-07-13", "endDate": "2026-07-19", "amount": 10000, "paymentCount": 1 },
      { "weekNo": 4, "startDate": "2026-07-20", "endDate": "2026-07-26", "amount": 15000, "paymentCount": 1 },
      { "weekNo": 5, "startDate": "2026-07-27", "endDate": "2026-07-31", "amount": 0,     "paymentCount": 0 }
    ],
    "categories": [
      { "categoryId": 6, "categoryName": "온라인쇼핑",  "amount": 15000, "paymentCount": 1, "ratio": 29 },
      { "categoryId": 5, "categoryName": "대중교통",    "amount": 15000, "paymentCount": 1, "ratio": 29 },
      { "categoryId": 4, "categoryName": "게임",        "amount": 12000, "paymentCount": 1, "ratio": 23 },
      { "categoryId": 2, "categoryName": "카페·디저트", "amount": 10000, "paymentCount": 1, "ratio": 19 }
    ]
  },
  "watchSpending": {
    "paymentCount": 2,
    "amount": 27000,
    "totalPaymentCount": 4,
    "comparisonCount": 1,
    "categories": [
      { "categoryId": 6, "categoryName": "온라인쇼핑", "paymentCount": 1, "amount": 15000 },
      { "categoryId": 4, "categoryName": "게임",       "paymentCount": 1, "amount": 12000 }
    ]
  }
}
```

`categoryId`는 예시 값입니다. 실제 값은 DB의 업종 카테고리 아이디입니다.

## 화면 만들 때 알아야 할 것

### weeklyTrend의 `amount`는 null일 수 있습니다

**그 달의 모든 주차가 항상 내려갑니다.** 진행 중인 달이라도 막대 개수가 달라지지 않으니
축을 고정해서 그리시면 됩니다.

대신 **아직 오지 않은 주차는 `amount`와 `paymentCount`가 `null`입니다.** `0`이 아닙니다.

- `0` → 그 주가 지났는데 결제가 없었다
- `null` → 그 주가 아직 오지 않았다

두 개를 같은 막대로 그리면 "이번 달 남은 주에 0원 썼다"로 읽힙니다. `null`은 흐린 색이나
점선 같은 별도 처리를 권합니다. 완료된 달에는 `null`이 하나도 없습니다.

주차는 **월요일에 시작해 일요일에 끝나고 그 달의 1일과 말일에서만 잘립니다.**
그래서 달에 따라 4~6주가 나옵니다. 2026년 7월은 5주, 8월은 6주입니다.

### categories는 정렬되어 옵니다

금액 내림차순 → 횟수 내림차순 → 이름 오름차순입니다. **자르지 않고 전부 보냅니다.**
상위 4개만 보여주고 `전체 보기`로 펼치는 건 화면에서 `slice` 하시면 됩니다.

`ratio`는 정수 %입니다. **반올림 때문에 합이 100이 안 될 수 있습니다.** 도넛 차트를
`conic-gradient`로 그리신다면 마지막 조각을 100%까지 채우는 보정이 필요합니다.

### 빈 달은 오류가 아닙니다

활동이 없는 달도 200이고 금액은 `0`, 배열은 `[]`입니다. `weeklyTrend`는 그대로 옵니다.
빈 상태 화면은 `spending.paymentCount === 0`으로 판단하시면 됩니다.

### audience.ageBand

`JUNIOR`(만 7~12세) / `TEEN`(만 13~18세). 서버가 자녀의 생년월일로 계산합니다.

- **집계 숫자는 이 값에 따라 달라지지 않습니다.** 문구 난이도와 밀도만 바꾸시면 됩니다.
- 과거 달을 봐도 **현재** 연령이 내려갑니다.
- 부모가 조회해도 **자녀 기준** 값이 내려갑니다.

### availableMonths

가입 월부터 현재 월까지, **최신 월이 먼저**입니다. 미래 월은 오지 않습니다.

`status`는 지금 `IN_PROGRESS` / `COMPLETED` 두 가지입니다. 정의서 4.4의 `기록 없음`은
2차에서 추가됩니다. 그때까지 활동이 없는 달도 `COMPLETED`로 오고, 열면 빈 리포트입니다.

## 오류

| HTTP | `code` | 언제 |
| --- | --- | --- |
| 400 | `MONEY_REPORT_INVALID_MONTH` | `month`가 `yyyy-MM`이 아님 |
| 400 | `MONEY_REPORT_FUTURE_MONTH` | 미래 월 — 현재 월로 되돌리고 토스트 |
| 400 | `MONEY_REPORT_MONTH_BEFORE_JOIN` | 가입 이전 월 |
| 403 | `AUTH_FORBIDDEN` | 본인도 아니고 그 자녀의 부모도 아님 |
| 404 | `MONEY_REPORT_CHILD_NOT_FOUND` | 자녀 없음 |

`availableMonths`에 오는 월만 고르게 하면 400 두 개는 정상 흐름에서 발생하지 않습니다.

섹션별 부분 실패는 없습니다. 집계가 실패하면 전체가 500이므로 전체 오류 화면과
`다시 불러오기` 하나만 만드시면 됩니다.

## 로컬에서 확인하기

```
schema/teenymoney_schema_renamed.sql
  -> V020까지 마이그레이션 (파일명 순, V006_1 포함, V019 없음)
  -> seed/01_seed_valid_data.sql
  -> seed/02_seed_money_report_demo.sql
```

데모 계정 (비밀번호 전부 `Local1234!`)

| 계정 | 쓰임 |
| --- | --- |
| `report-junior@gmail.com` | JUNIOR 모드, 주의 업종 없음 |
| `report-teen@gmail.com` | TEEN 모드, 주의 업종 있음 (위 예시가 이 계정의 전월) |
| `report-empty@gmail.com` | 빈 상태 |
| `report-parent@naver.com` | 위 셋 전부의 부모. 부모 조회 확인용 |

시드는 `CURDATE()` 기준이라 "현재 월 / 전월 / 전전월"에 데이터가 있습니다.
현재 월 결제는 그 달 4~10일에만 있으므로, 월초에 조회하면 현재 월 합계가 0일 수 있습니다.
