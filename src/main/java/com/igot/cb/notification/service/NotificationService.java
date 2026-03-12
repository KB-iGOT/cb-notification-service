package com.igot.cb.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.notification.enums.NotificationReadStatus;

import java.util.List;
import java.util.Map;

import org.igot.common.ApiResponse;

public interface NotificationService {

    ApiResponse createNotification(JsonNode userNotificationDetail, String token);

    ApiResponse bulkCreateNotifications(JsonNode userNotificationDetail);

    ApiResponse readByUserIdAndNotificationId(String notificationId, String token);

    ApiResponse getNotificationsByUserIdAndLastXDays(String token, int days, int page, int size, NotificationReadStatus status, String subType);

    ApiResponse markNotificationsAsRead(String token, Map<String, Object> request);

    ApiResponse markNotificationsAsDeleted(String token, List<String> notificationIds);

    ApiResponse getUnreadNotificationCount(String token, int days);

    ApiResponse getResetNotificationCount(String token);

    /**
     * Creates bulk peer-validation notifications for the given request payload.
     *
     * @param requestBody map containing a {@code request} list of per-user notification payloads
     * @return {@link ApiResponse} with processed, skipped, and failed counts along with notification IDs
     */
    ApiResponse bulkCreatePeerValidationNotifications(Map<String, Object> requestBody);

    /**
     * Fetches paginated peer-validation records for the authenticated user from the table
     * corresponding to the given {@code subType}.
     *
     * @param token   x-auth-token of the requesting user
     * @param subType PEER_EVALUATION_ASSIGNED or PEER_REVIEW_ASSIGNED
     * @param days    how many past days to include
     * @param page    zero-based page index
     * @param size    page size
     * @return {@link ApiResponse} with paginated records and pagination metadata
     */
    ApiResponse getPeerValidationNotifications(String token, String subType, int days, int page, int size);

}
