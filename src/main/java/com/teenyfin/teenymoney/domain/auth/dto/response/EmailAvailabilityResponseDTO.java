package com.teenyfin.teenymoney.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class EmailAvailabilityResponseDTO {

    private final boolean available;

    private EmailAvailabilityResponseDTO(boolean available) {
        this.available = available;
    }

    public static EmailAvailabilityResponseDTO of(boolean available) {
        return new EmailAvailabilityResponseDTO(available);
    }
}
