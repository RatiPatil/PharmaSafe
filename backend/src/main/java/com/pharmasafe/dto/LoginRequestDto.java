package com.pharmasafe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequestDto {

  @NotBlank(message = "Username is required")
  @Size(max = 254, message = "Username or email cannot exceed 254 characters")
  private String username;

  @NotBlank(message = "Password is required")
  @Size(max = 128, message = "Password cannot exceed 128 characters")
  private String password;

  @Size(max = 32)
  private String applicationRole;

  @jakarta.validation.constraints.Pattern(regexp = "USER|ROLE_ADMIN")
  private String loginMode;

  public String getApplicationRole() {
    return applicationRole;
  }

  public void setApplicationRole(String value) {
    applicationRole = value;
  }

  public String getLoginMode() {
    return loginMode;
  }

  public void setLoginMode(String value) {
    loginMode = value;
  }

  public LoginRequestDto() {}

  public LoginRequestDto(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
