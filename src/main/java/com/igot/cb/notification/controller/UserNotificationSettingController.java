package com.igot.cb.notification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.notification.service.UserNotificationSettingService;
import com.igot.cb.util.Constants;

import org.igot.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notificationSetting")
public class UserNotificationSettingController {

    private UserNotificationSettingService userNotificationSettingService;

    public UserNotificationSettingController(UserNotificationSettingService userNotificationSettingService) {
        this.userNotificationSettingService = userNotificationSettingService;
    }

    @PostMapping("/upsert")
    public ResponseEntity<ApiResponse> upsertUserNotificationSetting(
            @RequestBody JsonNode userNotificationSettingDetail,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {

        ApiResponse response = userNotificationSettingService.upsertUserNotificationSetting(userNotificationSettingDetail, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read")
    public ResponseEntity<ApiResponse> getUserNotificationSettings(
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {

        ApiResponse response = userNotificationSettingService.getUserNotificationSettings(token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse> deleteUserNotificationSetting(
            @RequestBody JsonNode userNotificationSettingDetail,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {

        ApiResponse response = userNotificationSettingService.deleteUserNotificationSetting(userNotificationSettingDetail, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
