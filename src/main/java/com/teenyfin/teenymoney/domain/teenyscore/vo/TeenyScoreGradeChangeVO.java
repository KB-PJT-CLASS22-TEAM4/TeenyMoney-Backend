package com.teenyfin.teenymoney.domain.teenyscore.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 월간 등급 확정으로 등급이 실제로 바뀌는 자녀 한 명의 변경 전후 스냅샷이다.
 * 일괄 UPDATE는 무엇이 바뀌었는지 알려주지 않으므로, 갱신 직전에 이 목록을 먼저 읽어둔다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TeenyScoreGradeChangeVO {
    private Long childId;
    private String childName;
    /** 연결된 보호자가 없으면 null이다. 이 경우 자녀에게만 알린다. */
    private Long parentId;
    private Integer teenyScore;
    private String currentGradeName;
    private String newGradeName;
    private Integer currentGradeMinScore;
    private Integer newGradeMinScore;

    /** 등급표는 min_score가 클수록 높은 등급이므로 기준 점수만으로 상승 여부를 판단한다. */
    public boolean isUpgrade() {
        return newGradeMinScore > currentGradeMinScore;
    }
}
