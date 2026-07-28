package com.teenyfin.teenymoney.global.health.dto.response;

import lombok.Getter;

@Getter
public class DatabaseHealthResponseDTO {

    private final String database;


    private DatabaseHealthResponseDTO(String database) {

        this.database = database;
    }

    public static DatabaseHealthResponseDTO of(String database) {
        return new DatabaseHealthResponseDTO(database);
    }
}
