package com.pharmasafe.exception;

import com.pharmasafe.dto.AuthResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AuthResponseDto> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseDto.error("Invalid username or password"));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<AuthResponseDto> handleLocked(LockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED) // HTTP 423 Locked
                .body(AuthResponseDto.error(ex.getMessage()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<AuthResponseDto> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AuthResponseDto.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AuthResponseDto> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AuthResponseDto.error("Access denied: insufficient permissions"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AuthResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDto.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<AuthResponseDto> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDto.error(ex.getMessage()));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<AuthResponseDto> handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDto.error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<AuthResponseDto> handleConcurrentUpdate(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(AuthResponseDto.error("This record changed during your action. Refresh and try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthResponseDto> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error("An unexpected internal error occurred. Please contact system support."));
    }
}
