package com.pharmasafe.dto;

public class AuthResponseDto {

    private boolean success;
    private String message;
    private UserResponseDto user;

    public AuthResponseDto() {
    }

    public AuthResponseDto(boolean success, String message, UserResponseDto user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public static AuthResponseDto success(String message, UserResponseDto user) {
        return new AuthResponseDto(true, message, user);
    }

    public static AuthResponseDto error(String message) {
        return new AuthResponseDto(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UserResponseDto getUser() {
        return user;
    }

    public void setUser(UserResponseDto user) {
        this.user = user;
    }
}
