package com.igot.cb.notification.controller;

import com.igot.cb.notification.service.NotificationService;

import org.igot.common.ApiRespParam;
import org.igot.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static com.igot.cb.util.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BulkPeerValidationControllerTest {

    @InjectMocks
    private NotificationController notificationController;

    @Mock
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ApiResponse buildMockApiResponse(HttpStatus status, Map<String, Object> result) {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setId(PEER_VALIDATION_BULK_CREATE);
        apiResponse.setVer("v1");
        apiResponse.setTs(String.valueOf(System.currentTimeMillis()));
        apiResponse.setResponseCode(status);
        ApiRespParam params = new ApiRespParam(java.util.UUID.randomUUID().toString());
        params.setStatus(SUCCESS);
        apiResponse.setParams(params);
        apiResponse.setResult(result);
        return apiResponse;
    }

    @Test
    @DisplayName("successful bulk create returns 200 with wrapped response containing result")
    void bulkCreate_success() {
        Map<String, Object> result = Map.of(
                NOTIFICATIONS, List.of(Map.of(NOTIFICATION_ID, "n1")),
                PROCESSED_COUNT, 1,
                SKIPPED_COUNT, 0,
                FAILED_COUNT, 0
        );
        ApiResponse apiResponse = buildMockApiResponse(HttpStatus.OK, result);

        Map<String, Object> requestBody = Map.of(REQUEST, List.of(Map.of(USER_ID, "user-001")));
        when(notificationService.bulkCreatePeerValidationNotifications(requestBody)).thenReturn(apiResponse);

        ResponseEntity<Map<String, Object>> response =
                notificationController.createBulkPeerValidationNotification(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(PEER_VALIDATION_BULK_CREATE, body.get(ID));
        assertNotNull(body.get(VER));
        assertNotNull(body.get(TS));
        assertNotNull(body.get(PARAMS));
        assertEquals(HttpStatus.OK, body.get(RESPONSE_CODE));
        assertNotNull(body.get(RESULT));
        assertInstanceOf(Map.class, body.get(RESULT));
        assertEquals(1, ((Map<?, ?>) body.get(RESULT)).get(PROCESSED_COUNT));
    }

    @Test
    @DisplayName("validation error returns BAD_REQUEST status")
    void bulkCreate_validationError() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setId(PEER_VALIDATION_BULK_CREATE);
        apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        ApiRespParam params = new ApiRespParam(java.util.UUID.randomUUID().toString());
        params.setErrMsg(ERR_USER_ID_REQUIRED);
        apiResponse.setParams(params);

        Map<String, Object> requestBody = Map.of(REQUEST, List.of(Map.of()));
        when(notificationService.bulkCreatePeerValidationNotifications(requestBody)).thenReturn(apiResponse);

        ResponseEntity<Map<String, Object>> response =
                notificationController.createBulkPeerValidationNotification(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.BAD_REQUEST, body.get(RESPONSE_CODE));
    }

    @Test
    @DisplayName("internal server error returns 500 status")
    void bulkCreate_internalError() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setId(PEER_VALIDATION_BULK_CREATE);
        apiResponse.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiRespParam params = new ApiRespParam(java.util.UUID.randomUUID().toString());
        params.setErrMsg(INTERNAL_ERROR_MSG);
        apiResponse.setParams(params);

        Map<String, Object> requestBody = Map.of(REQUEST, List.of());
        when(notificationService.bulkCreatePeerValidationNotifications(requestBody)).thenReturn(apiResponse);

        ResponseEntity<Map<String, Object>> response =
                notificationController.createBulkPeerValidationNotification(requestBody);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    @DisplayName("response body does NOT contain duplicate response key")
    void bulkCreate_noDuplicateResponseKey() {
        ApiResponse apiResponse = buildMockApiResponse(HttpStatus.OK, Map.of(NOTIFICATIONS, List.of()));
        Map<String, Object> requestBody = Map.of(REQUEST, List.of(Map.of(USER_ID, "u1")));
        when(notificationService.bulkCreatePeerValidationNotifications(requestBody)).thenReturn(apiResponse);

        ResponseEntity<Map<String, Object>> response =
                notificationController.createBulkPeerValidationNotification(requestBody);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey(ID));
        assertTrue(body.containsKey(VER));
        assertTrue(body.containsKey(TS));
        assertTrue(body.containsKey(PARAMS));
        assertTrue(body.containsKey(RESPONSE_CODE));
        assertTrue(body.containsKey(RESULT));
        assertFalse(body.containsKey("response"));
        assertEquals(6, body.size());
    }

    @Test
    @DisplayName("service method is called exactly once with the request body")
    void bulkCreate_serviceCalledOnce() {
        ApiResponse apiResponse = buildMockApiResponse(HttpStatus.OK, Map.of());
        Map<String, Object> requestBody = Map.of(REQUEST, List.of());
        when(notificationService.bulkCreatePeerValidationNotifications(requestBody)).thenReturn(apiResponse);

        notificationController.createBulkPeerValidationNotification(requestBody);

        verify(notificationService, times(1)).bulkCreatePeerValidationNotifications(requestBody);
    }
}
