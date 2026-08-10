package com.teenyfin.teenymoney.domain.charge.mapper;

import com.teenyfin.teenymoney.domain.charge.vo.ChargeMethodVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChargeMethodMapper {

    // 새 결제수단 row를 INSERT한다.
    // 파라미터가 ChargeMethodVO 통째인 이유: TransferMapper.insertTransfer랑 같은 이유 -
    // 채워야 할 컬럼이 여러 개라 객체로 묶어 넘기는 게 깔끔함.
    // 리턴 타입은 void지만, 실행 후 ChargeMethod.getId()에 방금 생성된 PK가 자동으로 채워짐
    // (XML의 useGeneratedKeys="true" keyProperty="id" 덕분).

    void insert(ChargeMethodVO chargeMethod);

    // 특정 부모 회원의 결제수단을 전부 조회한다 (목록 API에서 씀).
    // 여러 건이라 List로 받음 - 등록된 게 하나도 없으면 빈 리스트(null 아님)가 옴.
    List<ChargeMethodVO> selectByParentId(@Param("parentId") Long parentId);

    // id 하나로 결제수단 한 건을 조회한다.
    // 주거래 지정/삭제 API에서 "이 id가 진짜 이 부모 소유가 맞는지" 확인할 때 씀.
    ChargeMethodVO selectById(@Param("id") Long id);

    // 특정 부모의 결제수단 전부를 is_primary = false로 내린다.
    // 새로운 주거래 수단을 지정하기 전에, 기존에 있던 주거래 수단의 표시를 먼저 지우는 용도.
    // "주거래는 항상 최대 1개"라는 규칙을 이 두 메서드(clearPrimary + setPrimary) 조합으로 지킴 -
    // 서비스 코드에서 반드시 같은 트랜잭션 안에서 순서대로 호출해야 함.
    void clearPrimaryByParentId(@Param("parentId") Long parentId);

    // 특정 결제수단 하나를 is_primary = true로 올린다.
    void setPrimary(@Param("id") Long id);

    // 결제수단의 상태를 바꾼다 (삭제 시 status = 'INACTIVE'로 바꾸는 용도).
    // 실제 DELETE는 안 하고 status만 바꾸는 이유는 ChargeMethodVO 주석 참고.
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
