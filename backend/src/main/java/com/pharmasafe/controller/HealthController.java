package com.pharmasafe.controller;

import com.pharmasafe.dto.HealthResponseDto;
import com.pharmasafe.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @org.springframework.beans.factory.annotation.Value("${RENDER_GIT_COMMIT:local}")
    private String deployedCommit;

    @GetMapping("/release")
    public java.util.Map<String,String> release() {
        return java.util.Map.of("commit", deployedCommit);
    }

    @GetMapping
    public ResponseEntity<HealthResponseDto> getHealth() {
        HealthResponseDto health = healthService.checkHealth();
        return ResponseEntity.ok(health);
    }
}
