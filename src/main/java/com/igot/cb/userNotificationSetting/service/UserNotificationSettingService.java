package com.igot.cb.userNotificationSetting.service;

import org.igot.common.ApiResponse;

import com.fasterxml.jackson.databind.JsonNode;

public interface UserNotificationSettingService {

    ApiResponse upsertUserNotificationSetting(JsonNode userNotificationDetail, String token);

    ApiResponse getUserNotificationSettings(String token);

    ApiResponse deleteUserNotificationSetting(JsonNode userNotificationDetail, String token);


}
