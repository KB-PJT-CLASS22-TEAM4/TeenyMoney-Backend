package com.teenyfin.teenymoney.domain.family.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 부모-자녀 연동 관계(T_MBR_CONN_R)의 상태 전이.
 *
 * 이 테이블의 불변조건(한 자녀에 활성 부모 하나, 연동 코드로만 생성, 해지 시 상태 전이)은
 * family 도메인이 지킨다. 매퍼 소유권 설계(3.1)의 "쓰기의 주인이 소유자다"에 따른 배치다.
 *
 * 생성(insertConnection)은 아직 MemberMapper 에 남아 있다. 이번 범위에서 옮기지 않는다.
 */
@Mapper
public interface FamilyConnectionMapper {

    /**
     * 활성 연결을 해제한다.
     *
     * WHERE 에 status='ACTIVE' 를 두는 이유: 이미 해제된 관계를 다시 해제하면 0 이 돌아와
     * 호출부가 "활성 연결이 없다"를 구분할 수 있다.
     */
    int deactivate(@Param("parentId") Long parentId,
                   @Param("childId") Long childId,
                   @Param("now") LocalDateTime now);

    /**
     * 해제됐던 연결을 되살린다.
     *
     * UNIQUE(parent_id, child_id) 때문에 같은 쌍은 행이 하나뿐이라, 재연결은 INSERT 가 아니라
     * 이 UPDATE 여야 한다. 처음 연결하는 쌍이면 0 이 돌아오고 호출부가 INSERT 경로로 간다.
     */
    int reactivate(@Param("parentId") Long parentId,
                   @Param("childId") Long childId,
                   @Param("now") LocalDateTime now);
}
