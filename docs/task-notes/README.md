# Task 노트

구현 플랜(`docs/*-pipeline.md` 등)의 Task를 하나 끝낼 때마다 남기는 기록이다.

플랜 문서는 **무엇을 어떤 순서로 만들지**를 적는다. 이 폴더는 **왜 그게 필요했고, 테스트로 무엇을 확인했으며, 그 확인이 왜 의미가 있는지**를 적는다. 코드만 봐서는 드러나지 않는 판단 근거를 남겨, 나중에 합류한 사람이나 몇 주 뒤의 자신이 "이건 왜 이렇게 됐나"를 되짚을 수 있게 하는 것이 목적이다.

## 파일 이름 규칙

```
<이슈>-task<번호>-<주제>.md
예) jwt-security-task1-jwtprovider.md
```

## 문서 구성

각 노트는 다음을 담는다.

1. **왜 이걸 구현했나** — 이 코드가 없으면 무엇이 불가능한지
2. **무엇을 만들었나** — 공개 계약(생성자·메서드·상수)
3. **테스트가 확인하는 것** — 테스트별로 *무엇을 넣어* *무엇이 나와야 하고* *왜 그게 맞는지*
4. **설계 판단과 근거** — 다른 선택지를 왜 버렸는지
5. **이 Task가 만들지 않은 것** — 범위 밖 항목

## 목록

- [하위2 Task 1 — JwtProvider와 JWT 프로퍼티](jwt-security-task1-jwtprovider.md)
- [하위2 Task 2 — MemberPrincipal과 JwtAuthenticationFilter](jwt-security-task2-authentication-filter.md)
- [하위2 Task 3 — 인증 401·인가 403 실패 응답 핸들러](jwt-security-task3-error-handlers.md)
