package com.igot.cb.notification.service;

import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.util.ApiResponse;

import java.util.Map;

/**
 * Service interface for mandatory notification operations.
 */
public interface MandatoryNotificationService {

    /**
     * Retrieves a paginated list of mandatory notifications filtered by date range, read status, and sub-type.
     *
     * @param token   auth token to identify the user
     * @param days    number of past days to include
     * @param page    zero-based page index
     * @param size    page size
     * @param status  read status filter (READ, UNREAD, or BOTH)
     * @param subType optional sub-type filter (e.g. ALERT, UPDATE)
     * @return ApiResponse containing paginated notifications and sub-type stats
     */
    ApiResponse getMandatoryNotificationsList(String token, int days, int page, int size, NotificationReadStatus status, String subType);

    /**
     * Retrieves the oldest unread mandatory notification for the authenticated user.
     *
     * @param token auth token to identify the user
     * @return ApiResponse containing a single notification or an empty map if none exist
     */
    ApiResponse getCurrentMandatoryNotification(String token);

    /**
     * Marks a single mandatory notification as read using the provided notification id and createdAt.
     *
     * @param token   auth token to identify the user
     * @param request request body containing 'id' and 'createdAt' fields
     * @return ApiResponse indicating success or failure
     */
    ApiResponse markMandatoryNotificationsAsRead(String token, Map<String, Object> request);
}
