package com.igot.cb.notification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.notification.service.UserNotificationSettingService;

import org.igot.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UserNotificationSettingControllerTest {

    @Mock
    private UserNotificationSettingService userNotificationSettingService;

    @InjectMocks
    private UserNotificationSettingController controller;

    private ObjectMapper objectMapper;
    private final String token = "dummy-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testUpsertUserNotificationSetting_returnsOkResponse() throws Exception {
        JsonNode requestJson = objectMapper.readTree("{\"setting\":\"email\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.setResult(Map.of("message", "Upserted successfully"));

        when(userNotificationSettingService.upsertUserNotificationSetting(any(JsonNode.class), any(String.class)))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = controller.upsertUserNotificationSetting(requestJson, token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Upserted successfully", response.getBody().getResult().get("message"));
    }

    @Test
    void testGetUserNotificationSettings_returnsOkResponse() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.setResult(Map.of("message", "Fetched successfully"));

        when(userNotificationSettingService.getUserNotificationSettings(token))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = controller.getUserNotificationSettings(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Fetched successfully", response.getBody().getResult().get("message"));
    }

    @Test
    void testDeleteUserNotificationSetting_returnsOkResponse() throws Exception {
        JsonNode requestJson = objectMapper.readTree("{\"setting\":\"sms\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.setResult(Map.of("message", "Deleted successfully"));

        when(userNotificationSettingService.deleteUserNotificationSetting(any(JsonNode.class), any(String.class)))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = controller.deleteUserNotificationSetting(requestJson, token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Deleted successfully", response.getBody().getResult().get("message"));
    }
}
