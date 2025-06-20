package com.igot.cb.userNotificationSetting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.util.ApiResponse;

public interface UserNotificationSettingService {

    ApiResponse upsertUserNotificationSetting(JsonNode userNotificationDetail, String token);

    ApiResponse getUserNotificationSettings(String token);

    ApiResponse deleteUserNotificationSetting(JsonNode userNotificationDetail, String token);


}
