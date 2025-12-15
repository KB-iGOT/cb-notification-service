package com.igot.cb.health.controller;

import com.igot.cb.health.service.HealthService;

import org.igot.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class HealthController {

    private HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse> healthCheck() throws Exception {
        ApiResponse response = healthService.checkHealthStatus();
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/liveness")
    public ResponseEntity<String> livenessCheck() throws Exception {
        return new ResponseEntity<>("Status ok", HttpStatus.OK);
    }
}
