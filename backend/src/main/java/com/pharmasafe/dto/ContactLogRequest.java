package com.pharmasafe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ContactLogRequest(
        @NotNull Instant contactAt,
        @NotBlank String contactType,
        @NotBlank String description,
        Long responsibleUserId
) {}
