package com.teenyfin.teenymoney.domain.family.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record FamilyLinkCodeResponseDTO(
        String code,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime expiresAt
) {
}
