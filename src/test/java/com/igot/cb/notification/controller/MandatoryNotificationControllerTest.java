package com.igot.cb.notification.controller;

import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.notification.service.MandatoryNotificationService;
import com.igot.cb.util.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MandatoryNotificationControllerTest {

    @InjectMocks
    private MandatoryNotificationController controller;

    @Mock
    private MandatoryNotificationService mandatoryNotificationService;

    private static final String AUTH_TOKEN = "test-auth-token";

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Nested
    @DisplayName("GET /mandatory/list")
    class GetMandatoryNotificationsListTests {
        @Test
        @DisplayName("should delegate to service and return 200")
        void delegatesToService_returns200() {
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setResponseCode(HttpStatus.OK);
            when(mandatoryNotificationService.getMandatoryNotificationsList(
                    any(), anyInt(), anyInt(), anyInt(),
                    any(), any()
            )).thenReturn(apiResponse);
            ResponseEntity<ApiResponse> result = controller.getMandatoryNotificationsList(
                    AUTH_TOKEN, 30, 0, 10, NotificationReadStatus.BOTH, null);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(apiResponse, result.getBody());
            verify(mandatoryNotificationService).getMandatoryNotificationsList(
                    AUTH_TOKEN, 30, 0, 10, NotificationReadStatus.BOTH, null);
        }

        @Test
        @DisplayName("should pass sub_type filter to service")
        void passesSubTypeToService() {
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setResponseCode(HttpStatus.OK);
            when(mandatoryNotificationService.getMandatoryNotificationsList(
                    any(), anyInt(), anyInt(), anyInt(),
                    any(), any()
            )).thenReturn(apiResponse);
            ResponseEntity<ApiResponse> result = controller.getMandatoryNotificationsList(
                    AUTH_TOKEN, 7, 1, 5, NotificationReadStatus.UNREAD, "ALERT");
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(mandatoryNotificationService).getMandatoryNotificationsList(
                    AUTH_TOKEN, 7, 1, 5, NotificationReadStatus.UNREAD, "ALERT");
        }

        @Test
        @DisplayName("should pass READ status filter to service")
        void passesReadStatusToService() {
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setResponseCode(HttpStatus.OK);
            when(mandatoryNotificationService.getMandatoryNotificationsList(
                    any(), anyInt(), anyInt(), anyInt(),
                    any(), any()
            )).thenReturn(apiResponse);
            ResponseEntity<ApiResponse> result = controller.getMandatoryNotificationsList(
                    AUTH_TOKEN, 30, 0, 10, NotificationReadStatus.READ, null);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(mandatoryNotificationService).getMandatoryNotificationsList(
                    AUTH_TOKEN, 30, 0, 10, NotificationReadStatus.READ, null);
        }
    }

    @Nested
    @DisplayName("GET /mandatory/current")
    class GetCurrentMandatoryNotificationTests {
        @Test
        @DisplayName("should delegate to service and return 200")
        void delegatesToService_returns200() {
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setResponseCode(HttpStatus.OK);
            when(mandatoryNotificationService.getCurrentMandatoryNotification(AUTH_TOKEN))
                    .thenReturn(apiResponse);
            ResponseEntity<ApiResponse> result = controller.getCurrentMandatoryNotification(AUTH_TOKEN);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(apiResponse, result.getBody());
            verify(mandatoryNotificationService).getCurrentMandatoryNotification(AUTH_TOKEN);
        }

        @Test
        @DisplayName("should return the response from service even on error")
        void serviceReturnsError_passedThrough() {
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
            when(mandatoryNotificationService.getCurrentMandatoryNotification(AUTH_TOKEN))
                    .thenReturn(apiResponse);
            ResponseEntity<ApiResponse> result = controller.getCurrentMandatoryNotification(AUTH_TOKEN);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(HttpStatus.BAD_REQUEST, result.getBody().getResponseCode());
        }
    }

    @Nested
    @DisplayName("PATCH /mandatory/read")
    class MarkMandatoryNotificationsAsReadTests {
        @Test
        @DisplayName("should delegate to service with request body and return 200")
        void delegatesToService_returns200() {
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> request = new HashMap<>();
            request.put("id", "n1");
            request.put("createdAt", "2026-02-28T08:00:00Z");
            requestBody.put("request", request);
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setResponseCode(HttpStatus.OK);
            when(mandatoryNotificationService.markMandatoryNotificationsAsRead(
                    any(), any()
            )).thenReturn(apiResponse);
            ResponseEntity<ApiResponse> result = controller.markMandatoryNotificationsAsRead(
                    AUTH_TOKEN, requestBody);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(apiResponse, result.getBody());
            verify(mandatoryNotificationService).markMandatoryNotificationsAsRead(AUTH_TOKEN, requestBody);
        }

        @Test
        @DisplayName("should pass through error response from service")
        void serviceReturnsError_passedThrough() {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("request", new HashMap<>());
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setResponseCode(HttpStatus.BAD_REQUEST);
            when(mandatoryNotificationService.markMandatoryNotificationsAsRead(
                    any(), any()
            )).thenReturn(apiResponse);
            ResponseEntity<ApiResponse> result = controller.markMandatoryNotificationsAsRead(
                    AUTH_TOKEN, requestBody);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(HttpStatus.BAD_REQUEST, result.getBody().getResponseCode());
        }
    }
}
