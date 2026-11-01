package com.pharmasafe.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record ReopenCaseRequest(@NotNull Long expectedRowVersion, @NotBlank @Size(max=10000) String reason,
 @NotNull Long assignedUserId, @NotNull @Future Instant dueAt, @NotBlank String password) {}
