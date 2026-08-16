package com.teenyfin.teenymoney.domain.allowance.dto.response;

// Jackson(스프링이 기본으로 쓰는 JSON 변환 라이브러리)한테 "JSON으로 나갈 때 이 이름을 써라"고
// 강제하는 어노테이션
import com.fasterxml.jackson.annotation.JsonProperty;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import lombok.Getter;

import java.time.LocalDate;

// 응답 DTO는 Setter가 없고 필드가 전부 final인 게 요청 DTO와의 차이 - 한 번 생성자에서
// 값을 채우면 그 뒤로 못 바꾸게 막아둔 것 (응답은 클라이언트가 값을 채워 보내는 게 아니라
// 서버가 만들어서 내려주기만 하니까 setter가 필요 없음).
@Getter
public class AllowanceScheduleResponseDTO {

    private final Long id;
    private final Long childId;
    private final Long amount;
    private final String cycleType;
    private final Integer paymentDay;
    private final LocalDate nextPaymentDate;

    // Lombok이 boolean 타입 필드의 getter를 만들 때 규칙: "isActive"라는 이름 그 자체를
    // getter로 그대로 씀(getIsActive()가 아니라 isActive()). 근데 Jackson이 JSON으로 직렬화할
    // 때는 "isActive()"라는 메서드 이름을 보고 필드명을 "active"로 추측해버릴 수 있어서
    // (getter 이름의 is/get 접두사를 떼고 남은 부분을 필드명으로 씀), JSON 키가 우리가 원하는
    // "isActive"가 아니라 "active"로 나갈 위험이 있음. 그래서 @JsonProperty("isActive")로
    // JSON 키 이름을 명시적으로 못박아둠.
    @JsonProperty("isActive")
    private final boolean active;


    private AllowanceScheduleResponseDTO(AllowanceScheduleVO vo) {
        this.id = vo.getId();
        this.childId = vo.getChildId();
        this.amount = vo.getAmount();
        this.cycleType = vo.getCycleType();
        this.paymentDay = vo.getPaymentDay();
        this.nextPaymentDate = vo.getNextPaymentDate();
        this.active = vo.isActive();
    }


    public static AllowanceScheduleResponseDTO of(AllowanceScheduleVO vo) {
        return new AllowanceScheduleResponseDTO(vo);
    }
}