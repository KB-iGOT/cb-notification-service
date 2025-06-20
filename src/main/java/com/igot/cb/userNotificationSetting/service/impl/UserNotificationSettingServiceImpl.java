package com.igot.cb.userNotificationSetting.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.transactional.redis.cache.CacheService;
import com.igot.cb.userNotificationSetting.entity.NotificationSettingEntity;
import com.igot.cb.userNotificationSetting.enums.NotificationType;
import com.igot.cb.userNotificationSetting.dto.NotificationSettingRequest;
import com.igot.cb.userNotificationSetting.repository.NotificationSettingRepository;
import com.igot.cb.userNotificationSetting.service.UserNotificationSettingService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.igot.cb.util.Constants.*;


@Service
@Slf4j
public class UserNotificationSettingServiceImpl implements UserNotificationSettingService {


    @Autowired
    AccessTokenValidator accessTokenValidator;

    @Autowired
    CacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Override
    public ApiResponse upsertUserNotificationSetting(JsonNode userNotificationDetail, String authToken) {
        log.info("NotificationSettingService::upsertUserNotificationSetting - started");

        ApiResponse response = ProjectUtil.createDefaultResponse(USER_NOTIFICATION_SETTING_UPSERT);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isBlank(userId)) {
                return buildErrorResponse(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            }

            NotificationSettingRequest request = validateAndExtractPayload(userNotificationDetail, response);
            if (ObjectUtils.isEmpty(request)) {
                return response;
            }

            Optional<NotificationSettingEntity> optionalEntity =
                    notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(userId, request.getNotificationType());

            NotificationSettingEntity entity;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            if (optionalEntity.isPresent()) {
                entity = optionalEntity.get();
                entity.setEnabled(request.isEnabled());
                entity.setUpdatedAt(now);
                log.info("Updating existing notification setting for user: {}", userId);
            } else {
                entity = NotificationSettingEntity.builder()
                        .userId(userId)
                        .notificationType(request.getNotificationType())
                        .enabled(request.isEnabled())
                        .createdAt(now)
                        .updatedAt(now)
                        .isDeleted(false)
                        .build();
                log.info("Creating new notification setting for user: {}", userId);
            }

            NotificationSettingEntity savedEntity = notificationSettingRepository.save(entity);

            Map<String, Object> result = Map.of(
                    NOTIFICATION_TYPE, savedEntity.getNotificationType(),
                    ENABLED, savedEntity.isEnabled()
            );

            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error while upserting user notification setting", e);
            return buildErrorResponse(response, Constants.FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }


    @Override
    public ApiResponse getUserNotificationSettings(String authToken) {
        log.info("NotificationSettingService::getUserNotificationSettings - started");

        ApiResponse response = ProjectUtil.createDefaultResponse(USER_NOTIFICATION_SETTING_READ);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isBlank(userId)) {
                return buildErrorResponse(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            }

            List<NotificationSettingEntity> existingSettings = notificationSettingRepository.findByUserIdAndIsDeletedFalse(userId);

            Map<String, NotificationSettingEntity> existingMap = existingSettings.stream()
                    .collect(Collectors.toMap(NotificationSettingEntity::getNotificationType, setting -> setting));

            List<Map<String, ? extends Serializable>> resultList = new ArrayList<>();


            for (NotificationType type : NotificationType.values()) {
                NotificationSettingEntity setting = existingMap.get(type.name());

                if (setting != null) {
                    resultList.add(Map.of(
                            NOTIFICATION_TYPE, setting.getNotificationType(),
                            ENABLED, setting.isEnabled()
                    ));
                } else {
                    resultList.add(Map.of(
                            NOTIFICATION_TYPE, type.name(),
                            ENABLED, true
                    ));
                }
            }

            Map<String, Object> result = Map.of(SETTINGS, resultList);

            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            log.info("Fetched user notification settings (merged): {}", result);

        } catch (Exception e) {
            log.error("Error while fetching user notification settings", e);
            return buildErrorResponse(response, Constants.FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }


    @Override
    public ApiResponse deleteUserNotificationSetting(JsonNode userNotificationDetail, String authToken) {
        log.info("NotificationSettingService::deleteUserNotificationSetting - started");

        ApiResponse response = ProjectUtil.createDefaultResponse(USER_NOTIFICATION_SETTING_DELETE);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isBlank(userId)) {
                return buildErrorResponse(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            }


            NotificationSettingRequest request = validateAndExtractPayload(userNotificationDetail, response);
            if (ObjectUtils.isEmpty(request)) {
                return response;
            }
            Optional<NotificationSettingEntity> optionalEntity =
                    notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(userId, request.getNotificationType());

            if (optionalEntity.isEmpty()) {
                return buildErrorResponse(response, "Notification setting not found", HttpStatus.NOT_FOUND);
            }

            NotificationSettingEntity entity = optionalEntity.get();
            entity.setDeleted(true);
            entity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            notificationSettingRepository.save(entity);

            Map<String, Object> result = Map.of(
                    USER_ID, userId,
                    NOTIFICATION_TYPE, request.getNotificationType(),
                    DELETED, true
            );

            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            log.info("Soft-deleted user notification setting: {}", result);

        } catch (Exception e) {
            log.error("Error while soft-deleting user notification setting", e);
            return buildErrorResponse(response, Constants.FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }


    private ApiResponse buildErrorResponse(ApiResponse response, String errorMessage, HttpStatus status) {
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrMsg(errorMessage);
        response.setResponseCode(status);
        return response;
    }

    private NotificationSettingRequest validateAndExtractPayload(JsonNode userNotificationDetail, ApiResponse response) {
        JsonNode requestNode = userNotificationDetail.path(Constants.REQUEST);

        if (!requestNode.isObject()) {
            buildErrorResponse(response, "Missing or invalid 'request' node in payload", HttpStatus.BAD_REQUEST);
            return null;
        }

        String notificationType = requestNode.path(Constants.NOTIFICATION_TYPE).asText(null);
        boolean enabled = requestNode.path(Constants.ENABLED).asBoolean(false);

        if (StringUtils.isBlank(notificationType)) {
            buildErrorResponse(response, "Notification type is required", HttpStatus.BAD_REQUEST);
            return null;
        }

        return new NotificationSettingRequest(notificationType, enabled);
    }


}












