package com.teenyfin.teenymoney.global.health.mapper;

import com.teenyfin.teenymoney.global.health.vo.DatabaseHealthVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthMapper {

    DatabaseHealthVO selectDatabaseHealth();
}