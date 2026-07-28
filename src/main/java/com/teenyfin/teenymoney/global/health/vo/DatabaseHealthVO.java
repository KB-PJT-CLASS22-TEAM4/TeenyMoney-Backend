package com.teenyfin.teenymoney.global.health.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DatabaseHealthVO {

    private Integer checkResult;
    private String databaseName;
}