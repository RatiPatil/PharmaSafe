package com.pharmasafe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ActionItemRequest(
        @NotBlank String description,
        @NotNull Long assignedUserId,
        @NotNull Instant dueAt
) {}
