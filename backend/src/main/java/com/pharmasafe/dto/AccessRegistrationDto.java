package com.pharmasafe.dto;
import jakarta.validation.constraints.*;
import java.util.Map;
public record AccessRegistrationDto(
 @NotBlank @Size(max=2000) String selectionToken,
 @NotBlank @Size(max=128) String fullName,
 @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}") String employeeId,
 @NotBlank @Email @Size(max=254) String email,
 @NotBlank @Pattern(regexp="[+0-9() .-]{7,32}") String mobileNumber,
 @NotBlank @Size(max=64) String department,
 @NotBlank @Size(max=128) String designation,
 @NotBlank @Size(max=128) String organization,
 @NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,64}") String username,
 @NotBlank @Size(min=12,max=72) String password,
 @NotBlank @Size(max=72) String confirmPassword,
 @NotBlank @Size(max=2000) String reason,
 @Size(max=2000) String additionalInformation,
 @NotNull @Size(max=12) Map<String,String> details
) {}
