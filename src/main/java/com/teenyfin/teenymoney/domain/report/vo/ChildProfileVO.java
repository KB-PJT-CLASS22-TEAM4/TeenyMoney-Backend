package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 리포트 계산에 필요한 자녀 정보. 연령 모드와 가입 월 두 가지만 쓴다.
 *
 * MyBatis 가 결과를 담는 VO 이므로 기본 생성자와 setter 가 필요하다.
 * @Builder 만 붙이면 package-private 전체 인자 생성자만 생겨서
 * "No constructor found ... matching [...]" 로 실패한다.
 * (@Builder 는 테스트에서 쓰기 편해서 함께 남겨둔다)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChildProfileVO {

    private Long id;
    private String role;
    private LocalDate birthDate;
    private LocalDateTime createdAt;
}
