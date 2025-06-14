package com.igot.cb.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.util.ApiResponse;


import java.util.List;
import java.util.Map;

public interface NotificationService {

    ApiResponse createNotification(JsonNode userNotificationDetail, String token);

    ApiResponse bulkCreateNotifications(JsonNode userNotificationDetail);

    ApiResponse readByUserIdAndNotificationId(String notificationId, String token);

    ApiResponse getNotificationsByUserIdAndLastXDays(String token, int days, int page, int size, NotificationReadStatus status, String sub_type);

    ApiResponse markNotificationsAsRead(String token, Map<String, Object> request);

    ApiResponse markNotificationsAsDeleted(String token, List<String> notificationIds);

    ApiResponse getUnreadNotificationCount(String token, int days);

    ApiResponse getResetNotificationCount(String token);


}
