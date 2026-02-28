package com.igot.cb.notification.controller;

import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.notification.service.MandatoryNotificationService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for mandatory notification endpoints.
 * Provides APIs to list, fetch the latest, and mark mandatory notifications as read.
 */
@RestController
@RequestMapping(Constants.ENDPOINT_BASE_NOTIFICATIONS)
@Slf4j
public class MandatoryNotificationController {

    private final MandatoryNotificationService mandatoryNotificationService;

    public MandatoryNotificationController(MandatoryNotificationService mandatoryNotificationService) {
        this.mandatoryNotificationService = mandatoryNotificationService;
    }

    /**
     * Returns a paginated list of mandatory notifications filtered by days, read status, and sub-type.
     */
    @GetMapping(Constants.ENDPOINT_MANDATORY_LIST)
    public ResponseEntity<ApiResponse> getMandatoryNotificationsList(
            @RequestHeader(Constants.X_AUTH_TOKEN) String token,
            @RequestParam(defaultValue = Constants.DEFAULT_NOTIFICATION_DAYS + "") int days,
            @RequestParam(defaultValue = Constants.DEFAULT_NOTIFICATION_PAGE + "") int page,
            @RequestParam(defaultValue = Constants.DEFAULT_NOTIFICATION_PAGE_SIZE + "") int size,
            @RequestParam(defaultValue = Constants.DEFAULT_NOTIFICATION_READ_STATUS) NotificationReadStatus status,
            @RequestParam(name = Constants.PARAM_SUB_TYPE, required = false) String subType) {
        ApiResponse response = mandatoryNotificationService.getMandatoryNotificationsList(token, days, page, size, status, subType);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    /**
     * Returns the oldest unread mandatory notification for the authenticated user.
     */
    @GetMapping(Constants.ENDPOINT_MANDATORY_CURRENT)
    public ResponseEntity<ApiResponse> getCurrentMandatoryNotification(
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = mandatoryNotificationService.getCurrentMandatoryNotification(token);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Marks a single mandatory notification as read using the provided notification id and createdAt.
     */
    @PatchMapping(Constants.ENDPOINT_MANDATORY_READ)
    public ResponseEntity<ApiResponse> markMandatoryNotificationsAsRead(
            @RequestHeader(Constants.X_AUTH_TOKEN) String token,
            @RequestBody Map<String, Object> requestBody) {
        ApiResponse response = mandatoryNotificationService.markMandatoryNotificationsAsRead(token, requestBody);
        return ResponseEntity.ok(response);
    }
}
