package com.teenyfin.teenymoney.global.health.service;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.health.dto.response.DatabaseHealthResponseDTO;
import com.teenyfin.teenymoney.global.health.mapper.HealthMapper;
import com.teenyfin.teenymoney.global.health.vo.DatabaseHealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthMapper healthMapper;

    public DatabaseHealthResponseDTO checkDatabaseHealth() {
        try {
            DatabaseHealthVO databaseHealthVO =
                    healthMapper.selectDatabaseHealth();

            validateDatabaseHealth(databaseHealthVO);

            return DatabaseHealthResponseDTO.of(
                    databaseHealthVO.getDatabaseName()
            );
        } catch (DataAccessException exception) {
            log.error(
                    "데이터베이스 헬스 체크 중 접근 오류가 발생했습니다.",
                    exception
            );

            throw new BusinessException(
                    CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
            );
        }
    }

    private void validateDatabaseHealth(
            DatabaseHealthVO databaseHealthVO
    ) {
        if (databaseHealthVO == null) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
            );
        }

        if (!Integer.valueOf(1).equals(
                databaseHealthVO.getCheckResult()
        )) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
            );
        }

        if (databaseHealthVO.getDatabaseName() == null
                || databaseHealthVO.getDatabaseName().isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
            );
        }
    }
}
