package com.igot.cb.health.controller;

import com.igot.cb.health.service.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.igot.common.ApiResponse;

class HealthControllerTest {

    @InjectMocks
    private HealthController healthController;

    @Mock
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testHealthCheck_returnsApiResponseWithCorrectStatus() throws Exception {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(healthService.checkHealthStatus()).thenReturn(mockResponse);

        ResponseEntity<?> response = healthController.healthCheck();

        assertEquals(mockResponse, response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(healthService, times(1)).checkHealthStatus();
    }

    @Test
    void testLivenessCheck_returnsStatusOk() throws Exception {
        ResponseEntity<?> response = healthController.livenessCheck();

        assertEquals("Status ok", response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}