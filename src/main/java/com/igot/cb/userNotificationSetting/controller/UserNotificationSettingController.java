package com.igot.cb.userNotificationSetting.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.userNotificationSetting.service.UserNotificationSettingService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificationSetting")
public class UserNotificationSettingController {

    @Autowired
    private UserNotificationSettingService userNotificationSettingService;


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
