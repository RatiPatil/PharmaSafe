package com.pharmasafe.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record FollowUpRequest(@NotNull Long expectedRowVersion, @NotBlank @Pattern(regexp="FOLLOW_UP|AMENDMENT") String kind,
 @NotNull LocalDate receivedDate, LocalDate safetyReceivedDate, boolean significant,
 @NotBlank @Size(max=10000) String justification, @NotBlank @Size(max=10000) String summary,
 @Size(max=255) String sourceReference, Long relatedActionItemId) {}
