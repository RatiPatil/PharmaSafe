package com.pharmasafe.controller;

import com.pharmasafe.dto.AuthResponseDto;
import com.pharmasafe.dto.CsrfTokenDto;
import com.pharmasafe.dto.LoginRequestDto;
import com.pharmasafe.dto.UserResponseDto;
import com.pharmasafe.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequest,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        UserResponseDto user = authenticationService.login(loginRequest, request, response);
        return ResponseEntity.ok(AuthResponseDto.success("Authentication successful", user));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponseDto> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(request, response);
        return ResponseEntity.ok(AuthResponseDto.success("Logout successful", null));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        UserResponseDto currentUser = authenticationService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(currentUser);
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenDto> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            return ResponseEntity.ok(new CsrfTokenDto(csrfToken.getToken(), csrfToken.getHeaderName(), csrfToken.getParameterName()));
        }
        return ResponseEntity.ok(new CsrfTokenDto("", "X-XSRF-TOKEN", "_csrf"));
    }
}
