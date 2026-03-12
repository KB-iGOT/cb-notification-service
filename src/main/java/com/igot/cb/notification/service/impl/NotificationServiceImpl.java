package com.igot.cb.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.notification.entity.NotificationSettingEntity;
import com.igot.cb.notification.enums.NotificationCategory;
import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.notification.enums.NotificationSubCategory;
import com.igot.cb.notification.enums.NotificationSubType;
import com.igot.cb.notification.enums.NotificationType;
import com.igot.cb.notification.repository.NotificationSettingRepository;
import com.igot.cb.notification.service.NotificationService;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;
import io.micrometer.common.util.StringUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.igot.cb.util.Constants.*;


@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private AccessTokenValidator accessTokenValidator;
    private CassandraOperation cassandraOperation;
    private ObjectMapper objectMapper;
    private NotificationSettingRepository notificationSettingRepository;
    private CbServerProperties cbServerProperties;
    public NotificationServiceImpl(AccessTokenValidator accessTokenValidator, CassandraOperation cassandraOperation,
            ObjectMapper objectMapper, NotificationSettingRepository notificationSettingRepository,
            CbServerProperties cbServerProperties) {
        this.accessTokenValidator = accessTokenValidator;
        this.cassandraOperation = cassandraOperation;
        this.objectMapper = objectMapper;
        this.notificationSettingRepository = notificationSettingRepository;
        this.cbServerProperties = cbServerProperties;
    }

    private final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Override
    public ApiResponse createNotification(JsonNode userNotificationDetail, String authToken) {
        log.info("NotificationService::createNotification: inside the method");
        ApiResponse outgoingResponse = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_CREATE);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(outgoingResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            JsonNode requestNode = userNotificationDetail.get(Constants.REQUEST);
            if (ObjectUtils.isEmpty(requestNode) || !requestNode.isObject()) {
                log.warn(Constants.INVALID_REQUEST_ERR_MSG, userNotificationDetail.toString());
                updateErrorDetails(outgoingResponse, Constants.INVALID_PAYLOAD_ERR_MSG, HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            String notificationType = requestNode.path(TYPE).asText(null);
            if (StringUtils.isBlank(notificationType)) {
                updateErrorDetails(outgoingResponse, "Notification type is required", HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            Optional<NotificationSettingEntity> settingOpt =
                    notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(userId, notificationType);

            if (settingOpt.isPresent() && !settingOpt.get().isEnabled()) {
                log.info("User '{}' has disabled notification type '{}'. Skipping notification creation.", userId, notificationType);
                outgoingResponse.setResponseCode(HttpStatus.OK);
                outgoingResponse.getParams().setErrMsg("Notification not created as it is disabled by the user.");
                outgoingResponse.getParams().setStatus(Constants.SUCCESS);
                return outgoingResponse;
            }

            ZoneId zoneId = ZoneId.of(UTC);
            Instant instant = LocalDateTime.now().atZone(zoneId).toInstant();

            Map<String, Object> dbMap = new HashMap<>();
            dbMap.put(Constants.NOTIFICATION_ID, java.util.UUID.randomUUID().toString());
            dbMap.put(Constants.USER_ID, userId);
            dbMap.put(Constants.CREATED_AT, instant);
            dbMap.put(Constants.UPDATED_AT, instant);
            dbMap.put(Constants.IS_DELETED, false);
            dbMap.put(Constants.READ, false);
            dbMap.put(Constants.READ_AT, null);

            if (ObjectUtils.isNotEmpty(requestNode) && requestNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = requestNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode valueNode = entry.getValue();
                    if (valueNode.isValueNode()) {
                        dbMap.put(entry.getKey(), valueNode.asText());
                    } else {
                        dbMap.put(entry.getKey(), valueNode.toString());
                    }
                }
            } else {
                log.warn(Constants.INVALID_REQUEST_ERR_MSG, userNotificationDetail.toString());
                outgoingResponse.getParams().setErrMsg(Constants.INVALID_PAYLOAD_ERR_MSG);
                outgoingResponse.getParams().setStatus(Constants.FAILED);
                outgoingResponse.setResponseCode(HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            Object res = cassandraOperation.insertRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_NOTIFICATION,
                    dbMap
            );
            log.info("Inserted notification: {}", res.toString());


            incrementUnreadCountManually(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_UNREAD_NOTIFICATION_COUNT, userId);

            Map<String, Object> responseMap = new HashMap<>(dbMap);
            Map<String, Object> resultMap = prepareNotificationResponse(responseMap);

            outgoingResponse.setResponseCode(HttpStatus.OK);
            outgoingResponse.setResult(resultMap);
            log.info("NotificationService::createNotification saved successfully");

        } catch (Exception e) {
            log.error("Error while saving notification to Cassandra: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse, "Internal server error while saving notification data",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return outgoingResponse;
    }

    @Override
    public ApiResponse bulkCreateNotifications(JsonNode userNotificationDetail) {
        log.info("NotificationService::bulkCreateNotification: Bulk notification creation started");
        ApiResponse outgoingResponse = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_BULK_CREATE);

        try {
            JsonNode requestNode = userNotificationDetail.get(Constants.REQUEST);
            if (ObjectUtils.isEmpty(requestNode) || !requestNode.isObject()) {
                log.warn(Constants.INVALID_REQUEST_ERR_MSG, userNotificationDetail.toString());
                updateErrorDetails(outgoingResponse, Constants.INVALID_PAYLOAD_ERR_MSG, HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            JsonNode userIdsNode = requestNode.get(USER_IDS);
            if (ObjectUtils.isEmpty(userIdsNode) || !userIdsNode.isArray()) {
                log.warn("Missing or invalid 'user_ids' in request");
                updateErrorDetails(outgoingResponse, "'user_ids' must be a non-empty list", HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }


            String notificationType = requestNode.path(TYPE).asText(null);
            if (StringUtils.isBlank(notificationType)) {
                log.warn("Missing 'notification_type' in payload");
                updateErrorDetails(outgoingResponse, "'notification_type' is required", HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            if (userIdsNode.size() > MAX_USER_LIMIT) {
                log.warn("Too many user_ids in request: {}", userIdsNode.size());
                updateErrorDetails(outgoingResponse, "Cannot send notifications to more than 100 users in a single request", HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            NotificationSubCategory subCategory = NotificationSubCategory.valueOf(requestNode.get(SUB_CATEGORY).asText());


            if (isGlobalSubCategory(subCategory)) {
                return createGlobalNotification(subCategory, requestNode);
            }


            List<Map<String, Object>> notificationRecords = new ArrayList<>();
            ZoneId zoneId = ZoneId.of(UTC);
            Instant instant = LocalDateTime.now().atZone(zoneId).toInstant();

            List<String> userIdsForCountUpdate = new ArrayList<>();

            for (JsonNode userIdNode : userIdsNode) {
                JsonNode idNode = userIdNode.get(USER_ID);
                String userId = (idNode != null) ? idNode.asText() : null;

                if (StringUtils.isEmpty(userId)) {
                    log.warn("Empty user_id encountered in request");
                    continue;
                }

                Optional<NotificationSettingEntity> settingOpt =
                        notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(userId, notificationType);

                if (settingOpt.isPresent() && !settingOpt.get().isEnabled()) {
                    log.info("NotificationType '{}' is disabled for user '{}', skipping notification", notificationType, userId);
                    continue;
                }

                userIdsForCountUpdate.add(userId);

                Map<String, Object> dbMap = new HashMap<>();
                dbMap.put(Constants.NOTIFICATION_ID, java.util.UUID.randomUUID().toString());
                dbMap.put(Constants.USER_ID, userId);
                dbMap.put(Constants.CREATED_AT, instant);
                dbMap.put(Constants.UPDATED_AT, instant);
                dbMap.put(Constants.IS_DELETED, false);
                dbMap.put(Constants.READ, false);
                dbMap.put(Constants.READ_AT, null);

                JsonNode messageNode = requestNode.get(MESSAGE);
                if (messageNode != null) {
                    JsonNode dataNode = messageNode.get(DATA);
                    if (dataNode != null && dataNode.isObject()) {
                        ((ObjectNode) dataNode).put(COUNT, 1);
                    } else {
                        ObjectNode newDataNode = ((ObjectNode) messageNode).putObject(DATA);
                        newDataNode.put(COUNT, 1);
                    }
                }

                Iterator<Map.Entry<String, JsonNode>> fields = requestNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String key = entry.getKey();
                    JsonNode valueNode = entry.getValue();

                    if (!USER_IDS.equals(key)) {
                        dbMap.put(key, valueNode.isValueNode() ? valueNode.asText() : valueNode.toString());
                    }
                }
                notificationRecords.add(dbMap);
                if (subCategory.isShouldClub()) {
                    clubNotification(subCategory, userId, requestNode);
                }
            }

            String tableName = null;
            if (subCategory.isShouldClub()) {
                tableName = TABLE_INDIVIDUAL_NOTIFICATION;
            } else {
                tableName = TABLE_USER_NOTIFICATION;
            }

            Object insertResponse = cassandraOperation.insertBulkRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    tableName,
                    notificationRecords
            );


            if (insertResponse instanceof ApiResponse apiResponse &&
                    Constants.FAILED.equals(apiResponse.get(Constants.RESPONSE))) {
                log.error("Bulk notification insertion failed: {}", apiResponse.getParams().getErrMsg());
                updateErrorDetails(outgoingResponse, "Failed to insert notifications", HttpStatus.INTERNAL_SERVER_ERROR);
                return outgoingResponse;
            }

            for (String userId : userIdsForCountUpdate) {
                incrementUnreadCountManually(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_UNREAD_NOTIFICATION_COUNT, userId);
            }

            List<Map<String, Object>> responseList = notificationRecords.stream()
                    .map(this::prepareNotificationResponse)
                    .toList();

            outgoingResponse.setResponseCode(HttpStatus.OK);
            outgoingResponse.setResult(Map.of(Constants.NOTIFICATIONS, responseList));
            log.info("NotificationService::bulkCreateNotification: Successfully inserted {} notifications", responseList.size());

        } catch (Exception e) {
            log.error("Error during bulk notification creation: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse, "Internal server error while saving notifications", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return outgoingResponse;
    }

    private boolean isGlobalSubCategory(NotificationSubCategory subCategory) {
        return NotificationSubCategory.EVENT_PUBLISHED.equals(subCategory) ||
                NotificationSubCategory.COURSE_PUBLISHED.equals(subCategory) ||
                NotificationSubCategory.PROGRAM_PUBLISHED.equals(subCategory);
    }

    public ApiResponse createGlobalNotification(NotificationSubCategory subCategory, JsonNode requestNode) {
        log.info("Detected global notification for subCategory '{}'", subCategory);
        ApiResponse response = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_BULK_CREATE);

        try {
            Instant now = LocalDateTime.now().atZone(ZoneId.of(UTC)).toInstant();

            Map<String, Object> globalNotification = new HashMap<>();
            globalNotification.put(NOTIFICATION_ID, java.util.UUID.randomUUID().toString());
            globalNotification.put(CREATED_AT, now);
            globalNotification.put(UPDATED_AT, now);
            globalNotification.put(USER_ID, GLOBAL);
            globalNotification.put(READ, false);
            globalNotification.put(READ_AT, null);
            globalNotification.put(IS_DELETED, false);


            for (String field : List.of("type", "category", "sub_category", "sub_type", "source", "role", "template_id")) {
                JsonNode value = requestNode.get(field);
                if (value != null) {
                    globalNotification.put(field, value.isValueNode() ? value.asText() : value.toString());
                }
            }

            JsonNode messageNode = requestNode.get(MESSAGE);
            if (messageNode != null) {
                globalNotification.put(MESSAGE, messageNode.toString());
            }

            Object insertResponse = cassandraOperation.insertRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    TABLE_GLOBAL_NOTIFICATION,
                    globalNotification
            );

            if (insertResponse instanceof ApiResponse apiResponse &&
                    Constants.FAILED.equals(apiResponse.get(Constants.RESPONSE))) {
                log.error("Global notification insertion failed: {}", apiResponse.getParams().getErrMsg());
                updateErrorDetails(response, "Failed to insert global notification", HttpStatus.INTERNAL_SERVER_ERROR);
                return response;
            }

            Map<String, Object> formattedResponse = prepareNotificationResponse(globalNotification);
            response.setResponseCode(HttpStatus.OK);
            response.setResult(Map.of("notification", formattedResponse));
            log.info("Successfully inserted global notification for subCategory '{}'", subCategory);
        } catch (Exception e) {
            log.error("Error creating global notification: {}", e.getMessage(), e);
            updateErrorDetails(response, "Internal server error while saving global notification", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @SneakyThrows
    private void clubNotification(NotificationSubCategory notificationSubCategory, String userId, JsonNode requestNode) {
        Duration clubWindow = notificationSubCategory.clubWindow();
        List<Map<String, Object>> dbRecords = cassandraOperation.getRecordsByProperties(
                KEYSPACE_SUNBIRD,
                TABLE_USER_NOTIFICATION,
                Map.of(USER_ID, userId),
                null,
                MAX_NOTIFICATIONS_FETCH_FOR_READ
        );
        for (Map<String, Object> dbRecord : dbRecords) {
            String dbNotificationUserId = String.valueOf(dbRecord.get(USER_ID));
            JsonNode dbNotificationMessage = objectMapper.readTree(String.valueOf(dbRecord.get(MESSAGE)));
            JsonNode dbNotificationData = objectMapper.readTree(String.valueOf(dbRecord.get(MESSAGE))).get(DATA);

            if (!NotificationSubCategory.valueOf(String.valueOf(dbRecord.get(SUB_CATEGORY))).equals(notificationSubCategory)) {
                continue;
            }

            String dbNotificationClubKey = NotificationSubCategory.valueOf(String.valueOf(dbRecord.get(SUB_CATEGORY))).clubKey(dbNotificationData);
            String clubKey = notificationSubCategory.clubKey(requestNode.get(MESSAGE).get(DATA));

            if (((Instant) dbRecord.get(CREATED_AT)).isAfter(Instant.now().minus(clubWindow))
                    && dbNotificationUserId.equals(userId)
                    && dbNotificationClubKey.equals(clubKey)) {
                dbNotificationMessage = updateNotificationMessage(notificationSubCategory, dbNotificationMessage);
                cassandraOperation.updateRecord(KEYSPACE_SUNBIRD, TABLE_USER_NOTIFICATION,
                        Map.of(MESSAGE, objectMapper.writeValueAsString(dbNotificationMessage)),
                        Map.of(
                                USER_ID, userId,
                                CREATED_AT, dbRecord.get(CREATED_AT)
                        ));
                dbRecord.put(MESSAGE, dbNotificationMessage);
                return;
            }
        }

        ZoneId zoneId = ZoneId.of(UTC);
        Instant instant = LocalDateTime.now().atZone(zoneId).toInstant();
        Map<String, Object> dbMap = new HashMap<>();
        dbMap.put(Constants.NOTIFICATION_ID, java.util.UUID.randomUUID().toString());
        dbMap.put(Constants.USER_ID, userId);
        dbMap.put(Constants.CREATED_AT, instant);
        dbMap.put(Constants.UPDATED_AT, instant);
        dbMap.put(Constants.IS_DELETED, false);
        dbMap.put(Constants.READ, false);
        dbMap.put(Constants.READ_AT, null);

        Iterator<Map.Entry<String, JsonNode>> fields = requestNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (!USER_IDS.equals(key)) {
                dbMap.put(key, valueNode.isValueNode() ? valueNode.asText() : valueNode.toString());
            }
        }
        cassandraOperation.insertRecord(KEYSPACE_SUNBIRD, TABLE_USER_NOTIFICATION, dbMap);
    }

    private JsonNode updateNotificationMessage(NotificationSubCategory notificationSubCategory, JsonNode dbNotificationMessage) {
        ObjectNode rootNode = (ObjectNode) dbNotificationMessage;
        ObjectNode data = (ObjectNode) rootNode.get(DATA);
        int existingCount = data.get(COUNT).asInt();

        data.put(COUNT, existingCount + 1);

        rootNode.set(DATA, data);

        rootNode.put(BODY, constructMessage(notificationSubCategory, Map.of(COUNT, String.valueOf(existingCount + 1))));

        return rootNode;
    }

    private String constructMessage(NotificationSubCategory notificationSubCategory, Map<String, String> placeholders) {
        String customizedBody = notificationSubCategory.messageTemplate();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            customizedBody = customizedBody.replace("{" + entry.getKey() + "}", Optional.ofNullable(entry.getValue()).orElse(""));
        }
        return customizedBody;
    }


    @Override
    public ApiResponse readByUserIdAndNotificationId(String notificationId, String authToken) {
        log.info("NotificationService::readByUserIdAndNotificationId: inside the method");
        ApiResponse outgoingResponse = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_READ_NOTIFICATIONID);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);

            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(outgoingResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            List<Map<String, Object>> notifications = fetchNotifications(userId);

            Optional<Map<String, Object>> match = notifications.stream()
                    .filter(n -> notificationId.equals(n.get(NOTIFICATION_ID)))
                    .findFirst();

            if (match.isPresent()) {
                Map<String, Object> resultMap = prepareNotificationResponse(match.get());
                outgoingResponse.setResult(resultMap);
                outgoingResponse.setResponseCode(HttpStatus.OK);
            } else {
                outgoingResponse.getParams().setErrMsg("Notification not found for this user.");
                outgoingResponse.getParams().setStatus(Constants.SUCCESS);
                outgoingResponse.setResponseCode(HttpStatus.OK);
            }
            logger.info("NotificationServiceImpl::readByUserIdAndNotificationId retrieved successfully ");

        } catch (Exception e) {
            logger.error("Error while fetching readByUserIdAndNotificationId from Cassandra: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse, "Internal server error while fetching notification by userId",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return outgoingResponse;
    }

    @Override
    public ApiResponse getNotificationsByUserIdAndLastXDays(
            String authToken, int days, int page, int size,
            NotificationReadStatus status, String subTypeFilter) {

        log.info("NotificationService::getNotificationsByUserIdAndLastXDays - start");
        ApiResponse response = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_READ_N_DAYSID);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return response;
            }

            Optional<NotificationSettingEntity> settingOpt =
                    notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(userId, String.valueOf(NotificationType.IN_APP));


            if (settingOpt.isPresent() && !settingOpt.get().isEnabled()) {
                log.info("Notifications are disabled for user '{}', returning empty list", userId);
                Map<String, Object> emptyResult = Map.of(
                        NOTIFICATIONS, List.of(),
                        TOTAL_COUNT, 0,
                        PAGE, page,
                        SIZE, size,
                        HAS_NEXT_PAGE, false,
                        SUBTYPE_STATS, List.of()
                );

                response.setResponseCode(HttpStatus.OK);
                response.setResult(emptyResult);
                return response;
            }

            Instant fromDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toInstant();

            List<Map<String, Object>> userNotifications = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_NOTIFICATION,
                    Map.of(USER_ID, userId),
                    List.of(NOTIFICATION_ID, CREATED_AT, TYPE, MESSAGE, READ, ROLE, SOURCE, CATEGORY, SUB_CATEGORY, SUB_TYPE, IS_DELETED),
                    MAX_NOTIFICATIONS_FETCH_FOR_READ
            );

            List<Map<String, Object>> globalNotifications = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_GLOBAL_NOTIFICATION,
                    Map.of(USER_ID, GLOBAL),
                    List.of(NOTIFICATION_ID, CREATED_AT, TYPE, MESSAGE, READ, ROLE, SOURCE, CATEGORY, SUB_CATEGORY, SUB_TYPE, IS_DELETED),
                    MAX_NOTIFICATIONS_FETCH_FOR_READ
            );

            globalNotifications.forEach(n -> n.putIfAbsent(READ, false));

            Set<String> userNotifIds = userNotifications.stream()
                    .map(n -> (String) n.get(NOTIFICATION_ID))
                    .collect(Collectors.toSet());

            List<Map<String, Object>> merged = new ArrayList<>(userNotifications);

            for (Map<String, Object> globalNotif : globalNotifications) {
                String notifId = (String) globalNotif.get(NOTIFICATION_ID);
                if (!userNotifIds.contains(notifId)) {
                    merged.add(globalNotif);
                }
            }

            List<Map<String, Object>> mergedFiltered = merged.stream()
                    .filter(n -> {
                        Instant createdAt = getInstant(n.get(CREATED_AT));
                        if (createdAt == null || createdAt.isBefore(fromDate)) return false;

                        Boolean isDeleted = (Boolean) n.get(IS_DELETED);
                        if (Boolean.TRUE.equals(isDeleted)) return false;

                        Boolean isRead = (Boolean) n.get(READ);
                        if (status == NotificationReadStatus.READ && !Boolean.TRUE.equals(isRead)) return false;
                        if (status == NotificationReadStatus.UNREAD && !Boolean.FALSE.equals(isRead)) return false;

                        return true;
                    })
                    .toList();

            List<Map<String, Object>> sortedMerged = mergedFiltered.stream()
                    .sorted((a, b) -> {
                        Instant t1 = getInstant(a.get(CREATED_AT));
                        Instant t2 = getInstant(b.get(CREATED_AT));
                        return t2.compareTo(t1);
                    })
                    .limit(MAX_NOTIFICATIONS_FETCH_FOR_READ)
                    .toList();


            Map<String, Map<String, Integer>> subTypeCountMap = new HashMap<>();
            for (Map<String, Object> notification : sortedMerged) {
                String cat = (String) notification.getOrDefault(SUB_TYPE, ALL);
                Boolean isRead = (Boolean) notification.get(READ);

                Map<String, Integer> counts = subTypeCountMap.computeIfAbsent(cat, k -> new HashMap<>());
                counts.put(READ, counts.getOrDefault(READ, 0) + (Boolean.TRUE.equals(isRead) ? 1 : 0));
                counts.put(UNREAD, counts.getOrDefault(UNREAD, 0) + (Boolean.FALSE.equals(isRead) ? 1 : 0));
            }

            List<Map<String, Object>> subTypeStats = subTypeCountMap.entrySet().stream()
                    .map(this::buildSubTypeStat)
                    .sorted(Comparator.comparingInt(stat -> getFixedOrderIndex((String) stat.get(NAME))))
                    .toList();


            List<Map<String, Object>> filteredBySubType = sortedMerged.stream()
                    .filter(notification -> {
                        if (StringUtils.isNotBlank(subTypeFilter)) {
                            String subType = (String) notification.getOrDefault(SUB_TYPE, ALL);
                            return subTypeFilter.equalsIgnoreCase(subType);
                        }
                        return true;
                    })
                    .limit(MAX_NOTIFICATIONS_FETCH_FOR_READ)
                    .toList();

            int total = filteredBySubType.size();
            int fromIndex = Math.min(page * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            if (fromIndex > toIndex) fromIndex = toIndex;

            List<Map<String, Object>> paginated = filteredBySubType.subList(fromIndex, toIndex);

            List<Map<String, Object>> processed = paginated.stream()
                    .map(this::prepareNotificationResponse)
                    .toList();

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(NOTIFICATIONS, processed);
            resultMap.put(TOTAL_COUNT, total);
            resultMap.put(PAGE, page);
            resultMap.put(SIZE, size);
            resultMap.put(HAS_NEXT_PAGE, toIndex < total);
            resultMap.put(SUBTYPE_STATS, subTypeStats);

            response.setResponseCode(HttpStatus.OK);
            response.setResult(resultMap);
            log.info("NotificationService::getNotificationsByUserIdAndLastXDays - success, total: {}", total);

        } catch (Exception e) {
            log.error("Error fetching notifications: {}", e.getMessage(), e);
            updateErrorDetails(response,
                    "Internal server error while fetching notification list",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    public Instant getInstant(Object value) {
        if (value instanceof Instant instantValue) {
            return instantValue;
        } else if (value instanceof Date dateValue) {
            return (dateValue).toInstant();
        } else if (value instanceof String strValue) {
            try {
                return Instant.parse(strValue);
            } catch (Exception e) {
                log.warn("Invalid created_at format: {}", value);
            }
        }
        return null;
    }


    private Map<String, Object> buildSubTypeStat(Map.Entry<String, Map<String, Integer>> entry) {
        Map<String, Object> stat = new HashMap<>();
        stat.put(NAME, entry.getKey());
        stat.put(READ, entry.getValue().getOrDefault(READ, 0));
        stat.put(UNREAD, entry.getValue().getOrDefault(UNREAD, 0));
        return stat;
    }

    private int getFixedOrderIndex(String subType) {
        try {
            if (subType != null) {
                return NotificationSubType.valueOf(subType.toUpperCase()).ordinal();
            }
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
        return 0;
    }


    @Override
    public ApiResponse markNotificationsAsRead(String authToken, Map<String, Object> request) {
        log.info("NotificationService::markNotificationsAsRead - Incoming request: {}", request);

        ApiResponse response = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_READ_UPDATEID);
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);

        if (StringUtils.isEmpty(userId)) {
            updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
            return response;
        }

        String type = (String) request.get(TYPE);
        if (StringUtils.isBlank(type)) {
            updateErrorDetails(response, "Request type must be provided (all or individual)", HttpStatus.BAD_REQUEST);
            return response;
        }

        String action = (String) request.get(ACTION);

        try {
            List<String> notificationIds;
            List<Map<String, Object>> insertedAndMarked = new ArrayList<>();


            if (GLOBAL.equalsIgnoreCase(action)) {
                if (ALL.equalsIgnoreCase(type)) {
                    log.info("Global action with type 'all' - inserting and marking global notifications as read for user {}", userId);
                    List<Map<String, Object>> globalNotifications = fetchGlobalNotifications(MAX_NOTIFICATIONS_FETCH_FOR_READ);
                    insertedAndMarked = insertAndMarkGlobalNotificationsAsRead(userId, globalNotifications);
                    response.getParams().setErrMsg("Global notifications marked as read and inserted");
                    response.getParams().setStatus(Constants.SUCCESS);
                    response.setResponseCode(HttpStatus.OK);
                    response.setResult(Map.of(Constants.NOTIFICATIONS, insertedAndMarked));
                    return response;
                }else if (INDIVIDUAL.equalsIgnoreCase(type)) {
                    log.info("Global action with type 'individual' - inserting and marking global notifications as read for user {}", userId);
                    notificationIds = extractIndividualNotificationIds(request, response);
                    if (CollectionUtils.isEmpty(notificationIds)) return response;
                    List<Map<String, Object>> globalNotifications = fetchGlobalNotifications(MAX_NOTIFICATIONS_FETCH_FOR_READ);
                    List<Map<String, Object>> targetGlobals = globalNotifications.stream()
                            .filter(n -> notificationIds.contains(n.get(NOTIFICATION_ID)))
                            .collect(Collectors.toList());

                    if (targetGlobals.isEmpty()) {
                        updateErrorDetails(response, "No matching global notifications found for provided IDs", HttpStatus.NOT_FOUND);
                        return response;
                    }

                    insertedAndMarked = insertAndMarkGlobalNotificationsAsRead(userId, targetGlobals);
                    response.getParams().setErrMsg("Selected global notifications marked as read and inserted");
                    response.getParams().setStatus(Constants.SUCCESS);
                    response.setResponseCode(HttpStatus.OK);
                    response.setResult(Map.of(Constants.NOTIFICATIONS, insertedAndMarked));
                    return response;

                } else {
                    updateErrorDetails(response, "Invalid type. Allowed values: all, individual", HttpStatus.BAD_REQUEST);
                    return response;
                }
            }

            List<Map<String, Object>> userNotifications = fetchNotifications(userId);
            if (ALL.equalsIgnoreCase(type)) {
                notificationIds = userNotifications.stream()
                        .map(n -> (String) n.get(NOTIFICATION_ID))
                        .collect(Collectors.toList());
                List<Map<String, Object>> globalNotifications = fetchGlobalNotifications(MAX_NOTIFICATIONS_FETCH_FOR_READ);
                insertedAndMarked = insertAndMarkGlobalNotificationsAsRead(userId, globalNotifications);
            } else if (INDIVIDUAL.equalsIgnoreCase(type)) {
                notificationIds = extractIndividualNotificationIds(request, response);
                if (CollectionUtils.isEmpty(notificationIds)) return response;
            } else {
                updateErrorDetails(response, "Invalid type. Allowed values: all, individual", HttpStatus.BAD_REQUEST);
                return response;
            }

            List<Map<String, Object>> updated = processReadUpdate(userId, userNotifications, notificationIds);
            updated.addAll(insertedAndMarked);
            response.getParams().setErrMsg("Notifications updated successfully");
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResponseCode(HttpStatus.OK);
            response.setResult(Map.of(Constants.NOTIFICATIONS, updated));

            log.info("Notifications marked as read successfully. Count: {}", updated.size());
        } catch (Exception e) {
            log.error("Unexpected error during markNotificationsAsRead: {}", e.getMessage(), e);
            updateErrorDetails(response, "Internal server error while updating notifications", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractIndividualNotificationIds(Map<String, Object> request, ApiResponse response) {
        Object idsObj = request.get("ids");
        if (idsObj instanceof List<?>) {
            return (List<String>) idsObj;
        } else {
            updateErrorDetails(response, "Missing or invalid 'ids' field for individual type", HttpStatus.BAD_REQUEST);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> processReadUpdate(
            String userId,
            List<Map<String, Object>> userNotifications,
            List<String> targetIds
    ) {
        List<Map<String, Object>> updated = new ArrayList<>();
        Instant now = Instant.now();

        for (String notificationId : targetIds) {
            Optional<Map<String, Object>> matchOpt = userNotifications.stream()
                    .filter(n -> notificationId.equals(n.get(NOTIFICATION_ID)))
                    .findFirst();

            if (matchOpt.isEmpty()) {
                log.warn("Notification ID {} not found for user {}", notificationId, userId);
                continue;
            }

            Map<String, Object> notification = matchOpt.get();
            boolean alreadyRead = Boolean.TRUE.equals(notification.get(READ));

            if (alreadyRead) {
                log.debug("Notification {} already marked as read. Skipping.", notificationId);
                continue;
            }

            Map<String, Object> updateMap = Map.of(
                    READ, true,
                    READ_AT, now
            );

            Map<String, Object> result = updateNotification(userId, notificationId, updateMap);

            if (Constants.SUCCESS.equalsIgnoreCase((String) result.get(Constants.RESPONSE))) {
                updated.add(Map.of(
                        ID, notificationId,
                        READ, true,
                        READ_AT, now.toString()
                ));
            } else {
                log.warn("Failed to update notification ID {} for user {}", notificationId, userId);
            }
        }

        return updated;
    }

    private List<Map<String, Object>> fetchGlobalNotifications(int limit) {
        return cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_GLOBAL_NOTIFICATION,
                Map.of(Constants.USER_ID, Constants.GLOBAL),
                null,
                limit
        );
    }

    private List<Map<String, Object>> insertAndMarkGlobalNotificationsAsRead(
            String userId, List<Map<String, Object>> globalNotifs) {

        List<Map<String, Object>> existingUserNotifs = cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_USER_NOTIFICATION,
                Map.of(USER_ID, userId),
                List.of(NOTIFICATION_ID),
                MAX_NOTIFICATIONS_FETCH_FOR_READ
        );

        Set<String> existingIds = existingUserNotifs.stream()
                .map(n -> (String) n.get(NOTIFICATION_ID))
                .collect(Collectors.toSet());

        List<Map<String, Object>> inserted = new ArrayList<>();
        Instant now = Instant.now();

        for (Map<String, Object> global : globalNotifs) {
            String notificationId = (String) global.get(NOTIFICATION_ID);

            if (existingIds.contains(notificationId)) {
                log.info("Notification {} already exists for user {}, skipping insert.", notificationId, userId);
                continue;
            }

            Map<String, Object> newUserNotif = new HashMap<>(global);
            newUserNotif.put(USER_ID, userId);
            newUserNotif.put(READ, true);
            newUserNotif.put(READ_AT, now);
            newUserNotif.put(CREATED_AT, global.get(CREATED_AT));
            newUserNotif.put(IS_DELETED, false);

            cassandraOperation.insertRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_NOTIFICATION,
                    newUserNotif
            );

            inserted.add(Map.of(
                    ID, notificationId,
                    READ, true,
                    READ_AT, now.toString()
            ));
        }

        return inserted;
    }


    @Override
    public ApiResponse markNotificationsAsDeleted(String authToken, List<String> notificationIds) {
        log.info("NotificationService::markNotificationsAsDeleted - ids: {}", notificationIds);

        ApiResponse outgoingResponse = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_DELETE);
        List<Map<String, Object>> updated = new ArrayList<>();

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(outgoingResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            String errMsg = validateNotificationReadRequest(notificationIds, outgoingResponse);
            if (StringUtils.isNotBlank(errMsg)) {
                return outgoingResponse;
            }


            for (String notificationId : notificationIds) {
                Map<String, Object> updateMap = Map.of(
                        IS_DELETED, true,
                        UPDATED_AT, Instant.now()
                );

                Map<String, Object> result = updateNotification(userId, notificationId, updateMap);


                if (Constants.SUCCESS.equalsIgnoreCase((String) result.get(Constants.RESPONSE))) {
                    updated.add(Map.of(
                            ID, notificationId,
                            IS_DELETED, true
                    ));
                } else {
                    log.info("Notification {} is already marked as deleted or has no created_at", notificationId);
                }
            }

            outgoingResponse.getParams().setErrMsg("Notifications marked as deleted successfully");
            outgoingResponse.getParams().setStatus(Constants.SUCCESS);
            outgoingResponse.setResponseCode(HttpStatus.OK);
            outgoingResponse.setResult(Map.of(NOTIFICATIONS, updated));

            logger.info("NotificationServiceImpl::markNotificationsAsDeleted  delete successfully ");

        } catch (Exception e) {
            logger.error("Error while fetching  markNotificationsAsDeleted delete from Cassandra: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse, "Internal server error while fetching markNotificationsAsDeleted  delete",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            return outgoingResponse;
        }
        return outgoingResponse;
    }

    @Override
    public ApiResponse getUnreadNotificationCount(String authToken, int days) {
        log.info("NotificationService::getUnreadNotificationCount: inside the method");

        ApiResponse outgoingResponse = ApiResponse.createDefaultResponse(USER_NOTIFICATION_UNREAD_COUNT);

        try {

            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(outgoingResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            ApiResponse daysValidationResponse = validateDays(days);
            if (daysValidationResponse != null && daysValidationResponse.getResponseCode() != null &&
                    !HttpStatus.OK.equals(daysValidationResponse.getResponseCode())) {
                return daysValidationResponse;
            }

            int unreadCount = 0;
            Map<String, Object> criteria = Map.of(Constants.USER_ID, userId);

            List<Map<String, Object>> countRecords = cassandraOperation.getRecordsByProperties(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_UNREAD_NOTIFICATION_COUNT,
                    criteria,
                    List.of(COUNT, UPDATED_AT),
                    1
            );

            if (countRecords != null && !countRecords.isEmpty()) {
                Map<String, Object> record = countRecords.get(0);
                if (record != null) {
                    Object countObj = record.get(COUNT);
                    if (countObj instanceof Number recordCount) {
                        unreadCount = recordCount.intValue();
                        Instant lastUpdated = record.get(UPDATED_AT) instanceof Instant recordUpdatedAt ? recordUpdatedAt : null;
                        if (lastUpdated != null ) {
                            int globalNotificationCount = fetchGlobalNotifications(MAX_NOTIFICATIONS_FETCH_FOR_COUNT).stream()
                                    .filter(n -> n.get(CREATED_AT) instanceof Instant recordCreatedAt && recordCreatedAt.isAfter(lastUpdated))
                                    .toList().size();
                            unreadCount += globalNotificationCount;
                        }
                    }
                }
            } else {
                List<Map<String,Object>> globalNotifications = fetchGlobalNotifications(MAX_NOTIFICATIONS_FETCH_FOR_COUNT);
                int globalCount = CollectionUtils.isNotEmpty(globalNotifications) ? globalNotifications.size() : 0;
                unreadCount = globalCount;
            }



            log.info("Fetched unread count for userId {}: {}", userId, unreadCount);
            outgoingResponse.setResponseCode(HttpStatus.OK);
            outgoingResponse.setResult(Map.of("unread", unreadCount));

        } catch (Exception e) {
            log.error("Error in getUnreadNotificationCount: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse,
                    "Internal server error while fetching unread notification count",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return outgoingResponse;
    }


    @Override
    public ApiResponse getResetNotificationCount(String authToken) {
        log.info("NotificationService::getResetNotificationCount - Start");

        ApiResponse response = ApiResponse.createDefaultResponse(USER_NOTIFICATION_UNREAD_RESET_COUNT);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);

            if (StringUtils.isBlank(userId)) {
                log.warn("User ID not found from token.");
                updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return response;
            }

            Map<String, Object> updateAttributes = new HashMap<>();
            updateAttributes.put(COUNT, 0);
            updateAttributes.put(UPDATED_AT, Instant.now());
            Map<String, Object> compositeKey = Map.of(Constants.USER_ID, userId);

            Map<String, Object> updateResponse = cassandraOperation.updateRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_UNREAD_NOTIFICATION_COUNT,
                    updateAttributes,
                    compositeKey
            );

            if (!Constants.SUCCESS.equals(updateResponse.get(Constants.RESPONSE))) {
                log.warn("Failed to reset unread count for userId: {}", userId);
            } else {
                log.info("Unread count successfully reset to 0 for userId: {}", userId);
            }

            response.setResponseCode(HttpStatus.OK);

        } catch (Exception e) {
            log.error("Exception in getResetNotificationCount: {}", e.getMessage(), e);
            updateErrorDetails(response,
                    "Internal server error while resetting unread notification count",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    private String validateNotificationReadRequest(List<String> ids, ApiResponse response) {
        if (org.springframework.util.CollectionUtils.isEmpty(ids)) {
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("Request must contain a non-empty list of notification IDs.");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return "Request must contain a non-empty list of notification IDs.";
        }

        if (ids.size() > Constants.MAX_NOTIFICATION_READ_BATCH_SIZE) {
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("You can only mark up to " + Constants.MAX_NOTIFICATION_READ_BATCH_SIZE + " notifications as read at a time.");
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return "You can only mark up to " + Constants.MAX_NOTIFICATION_READ_BATCH_SIZE + " notifications as read at a time.";
        }

        return "";
    }

    private void updateErrorDetails(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrMsg(errorMessage);
        response.setResponseCode(httpStatus);
    }

    private List<Map<String, Object>> fetchNotifications(String userId) {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put(USER_ID, userId);

        return cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD,
                Constants.TABLE_USER_NOTIFICATION,
                queryMap,
                null,
                MAX_NOTIFICATIONS_FETCH_FOR_READ
        );
    }


    private Map<String, Object> updateNotification(String userId, String notificationId, Map<String, Object> updateMap) {
        List<Map<String, Object>> records = fetchNotifications(userId);

        Optional<Map<String, Object>> match = records.stream()
                .filter(r -> notificationId.equals(r.get(NOTIFICATION_ID)))
                .findFirst();

        if (match.isPresent()) {
            Map<String, Object> notification = match.get();
            Instant createdAt = (Instant) notification.get(CREATED_AT);

            Map<String, Object> compositeKey = Map.of(
                    USER_ID, userId,
                    CREATED_AT, createdAt
            );

            return cassandraOperation.updateRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_NOTIFICATION,
                    updateMap,
                    compositeKey
            );
        }
        return Collections.emptyMap();
    }


    public Map<String, Object> prepareNotificationResponse(Map<String, Object> dbRecord) {
        Map<String, Object> resultMap = new HashMap<>(dbRecord);
        List<String> fieldsToRemove = Arrays.asList(
                Constants.IS_DELETED,
                Constants.UPDATED_AT,
                Constants.USER_ID,
                Constants.READ_AT,
                Constants.TEMPLATE_ID
        );
        fieldsToRemove.forEach(resultMap::remove);
        Object createdAtObj = resultMap.get(Constants.CREATED_AT);
        if (createdAtObj instanceof Instant instant) {
            resultMap.put(Constants.CREATED_AT, instant.toString());
        }
        Object messageObj = resultMap.get("message");

        if (messageObj instanceof String strMessage && messageObj != null) {
            try {
                JsonNode parsed = objectMapper.readTree(strMessage);
                resultMap.put("message", parsed);
                log.info("Message successfully parsed into JSON: {}", parsed.toPrettyString());
            } catch (Exception e) {
                log.warn("Could not parse message field as JSON: {}", e.getMessage());
            }
        } else {
            log.warn("Message field is not a valid string or is null.");
        }

        return resultMap;
    }


    private ApiResponse validateDays(int days) {
        ApiResponse response = new ApiResponse();
        if (days <= 0) {
            log.warn("Invalid 'days' parameter: {}", days);
            updateErrorDetails(response, "'days' parameter must be greater than 0", HttpStatus.BAD_REQUEST);
            return response;
        }
        return null;
    }

    private void incrementUnreadCountManually(String keyspace, String table, String userId) {
        try {
            Map<String, Object> whereClause = new HashMap<>();
            whereClause.put(USER_ID, userId);

            List<String> fields = Collections.singletonList(COUNT);
            List<Map<String, Object>> records = cassandraOperation.getRecordsByProperties(
                    keyspace, table, whereClause, fields, 1
            );

            int updatedCount = 1;

            if (!records.isEmpty() && records.get(0).get(COUNT) != null) {
                int currentCount = (int) records.get(0).get(COUNT);
                updatedCount = currentCount + 1;
            }

            Map<String, Object> updateAttributes = new HashMap<>();
            updateAttributes.put(COUNT, updatedCount);
            updateAttributes.put(UPDATED_AT, Instant.now());

            cassandraOperation.updateRecord(
                    keyspace,
                    table,
                    updateAttributes,
                    whereClause
            );

            log.info("Unread notification count updated for user {}: {}", userId, updatedCount);
        } catch (Exception e) {
            log.error("Error updating unread count for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Validates and processes a bulk peer-validation notification request.
     * Delegates to filtering, record building, and Cassandra persistence.
     *
     * @param requestBody map containing a {@code request} list of per-user notification payloads
     * @return {@link ApiResponse} with processed, skipped, and failed counts
     */
    @Override
    public ApiResponse bulkCreatePeerValidationNotifications(Map<String, Object> requestBody) {
        log.info("NotificationService::bulkCreatePeerValidationNotifications: Start");
        ApiResponse response = ApiResponse.createDefaultResponse(PEER_VALIDATION_BULK_CREATE);
        try {
            List<Map<String, Object>> requestList = (List<Map<String, Object>>) requestBody.get(REQUEST);
            String validationError = validatePeerValidationRequest(requestList);
            if (StringUtils.isNotBlank(validationError)) {
                log.warn("Validation failed for peer validation request: {}", validationError);
                updateErrorDetails(response, validationError, HttpStatus.BAD_REQUEST);
                return response;
            }
            List<Map<String, Object>> eligibleNotifications = new ArrayList<>();
            List<Map<String, Object>> actionRecords = new ArrayList<>();
            List<Map<String, Object>> skipped = new ArrayList<>();
            List<Map<String, Object>> failures = new ArrayList<>();
            filterAndBuildRecords(requestList, eligibleNotifications, actionRecords, skipped, failures);
            List<Map<String, Object>> notifications = persistPeerValidationNotifications(eligibleNotifications, actionRecords);
            populateBulkResponse(response, notifications, skipped, failures);
        } catch (Exception e) {
            log.error("Error during bulk peer validation notification creation: {}", e.getMessage(), e);
            updateErrorDetails(response, INTERNAL_ERROR_MSG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    /**
     * Populates the {@link ApiResponse} result with notification counts and lists
     * for processed, skipped, and failed entries.
     */
    private void populateBulkResponse(ApiResponse response, List<Map<String, Object>> notifications,
                                      List<Map<String, Object>> skipped, List<Map<String, Object>> failures) {
        response.setResponseCode(HttpStatus.OK);
        response.getParams().setStatus(SUCCESS);
        if (CollectionUtils.isEmpty(notifications)) {
            log.info("No eligible users found after filtering notification settings");
            response.getParams().setErrMsg(NO_ELIGIBLE_USERS_MSG);
        }
        response.setResult(Map.of(
                NOTIFICATIONS, notifications,
                PROCESSED_COUNT, notifications.size(),
                SKIPPED_COUNT, skipped.size(),
                SKIPPED, skipped,
                FAILED_COUNT, failures.size(),
                FAILED_KEY, failures
        ));
    }

    /**
     * Persists eligible notification records and action records to Cassandra,
     * then increments unread counts for all affected users.
     *
     * @return list of response entries built from the persisted notifications
     */
    private List<Map<String, Object>> persistPeerValidationNotifications(
            List<Map<String, Object>> eligibleNotifications, List<Map<String, Object>> actionRecords) {
        if (CollectionUtils.isEmpty(eligibleNotifications)) {
            return Collections.emptyList();
        }
        Set<String> uniqueUserIds = new LinkedHashSet<>();
        List<Map<String, Object>> notificationsForInsert = prepareRecordsForInsert(eligibleNotifications, uniqueUserIds);
        cassandraOperation.insertBulkRecord(KEYSPACE_SUNBIRD, TABLE_USER_NOTIFICATION, notificationsForInsert);
        cassandraOperation.insertBulkRecord(KEYSPACE_SUNBIRD, TABLE_PEER_VALIDATION_ACTIONS, actionRecords);
        bulkIncrementUnreadCounts(uniqueUserIds);
        return eligibleNotifications.stream().map(this::buildPeerValidationResponseEntry).toList();
    }

    /**
     * Copies eligible notifications into insert-ready maps and collects unique user IDs.
     *
     * @param uniqueUserIds set populated with each notification's user ID as a side-effect
     * @return mutable copies of the eligible notification maps ready for Cassandra bulk insert
     */
    private List<Map<String, Object>> prepareRecordsForInsert(
            List<Map<String, Object>> eligibleNotifications, Set<String> uniqueUserIds) {
        List<Map<String, Object>> notificationsForInsert = new ArrayList<>(eligibleNotifications.size());
        for (Map<String, Object> notification : eligibleNotifications) {
            uniqueUserIds.add((String) notification.get(USER_ID));
            notificationsForInsert.add(new HashMap<>(notification));
        }
        return notificationsForInsert;
    }

    /**
     * Fetches existing unread counts for the given users and upserts each count incremented by one.
     */
    private void bulkIncrementUnreadCounts(Set<String> userIds) {
        Map<String, Integer> existingCounts = fetchExistingUnreadCounts(userIds);
        List<Map<String, Object>> upsertRecords = buildUnreadCountUpsertRecords(userIds, existingCounts);
        cassandraOperation.insertBulkRecord(KEYSPACE_SUNBIRD, TABLE_UNREAD_NOTIFICATION_COUNT, upsertRecords);
        log.info("Bulk updated unread counts for {} users", userIds.size());
    }

    /**
     * Queries Cassandra for the current unread-notification count of each user in the given set.
     *
     * @return map of userId to current unread count (absent entries default to 0)
     */
    private Map<String, Integer> fetchExistingUnreadCounts(Set<String> userIds) {
        List<Map<String, Object>> countRecords = cassandraOperation.getRecordsByProperties(
                KEYSPACE_SUNBIRD, TABLE_UNREAD_NOTIFICATION_COUNT,
                Map.of(USER_ID, new ArrayList<>(userIds)),
                List.of(USER_ID, COUNT),
                userIds.size()
        );
        Map<String, Integer> existingCounts = new HashMap<>();
        for (Map<String, Object> countEntry : countRecords) {
            String uid = (String) countEntry.get(USER_ID);
            Object countObj = countEntry.get(COUNT);
            if (countObj instanceof Number num) {
                existingCounts.put(uid, num.intValue());
            }
        }
        return existingCounts;
    }

    /**
     * Builds upsert records by incrementing each user's existing unread count by one.
     *
     * @param existingCounts current counts per user; missing entries default to 0
     * @return list of records ready for Cassandra bulk upsert
     */
    private List<Map<String, Object>> buildUnreadCountUpsertRecords(
            Set<String> userIds, Map<String, Integer> existingCounts) {
        Instant now = Instant.now();
        List<Map<String, Object>> upsertRecords = new ArrayList<>(userIds.size());
        for (String uid : userIds) {
            int newCount = existingCounts.getOrDefault(uid, 0) + 1;
            Map<String, Object> countRecord = new LinkedHashMap<>();
            countRecord.put(USER_ID, uid);
            countRecord.put(COUNT, newCount);
            countRecord.put(UPDATED_AT, now);
            upsertRecords.add(countRecord);
        }
        return upsertRecords;
    }

    /**
     * Strips internal fields ({@code is_deleted}, {@code updated_at}, {@code read_at}) from a
     * notification record and formats {@code created_at} as an ISO-8601 string for the API response.
     */
    private Map<String, Object> buildPeerValidationResponseEntry(Map<String, Object> notificationRecord) {
        Map<String, Object> response = new HashMap<>(notificationRecord);
        response.remove(IS_DELETED);
        response.remove(UPDATED_AT);
        response.remove(READ_AT);
        Object createdAt = response.get(CREATED_AT);
        if (createdAt instanceof Instant instant) {
            response.put(CREATED_AT, instant.toString());
        }
        return response;
    }

    /**
     * Iterates the request list, skips users whose notification type is disabled,
     * and builds notification and action records for eligible users.
     * Failures during record construction are collected separately.
     */
    private void filterAndBuildRecords(
            List<Map<String, Object>> requestList,
            List<Map<String, Object>> eligibleNotifications,
            List<Map<String, Object>> actionRecords,
            List<Map<String, Object>> skipped,
            List<Map<String, Object>> failures) {
        Map<String, Map<String, NotificationSettingEntity>> userSettingsMap =
                cbServerProperties.isPeerValidationNotificationSettingCheckEnabled()
                        ? fetchUserNotificationSettings(requestList)
                        : Collections.emptyMap();
        for (Map<String, Object> request : requestList) {
            String userId = (String) request.get(USER_ID);
            String notificationType = (String) request.get(TYPE);
            if (isNotificationDisabled(userSettingsMap, userId, notificationType)) {
                log.info("NotificationType '{}' is disabled for user '{}', skipping", notificationType, userId);
                skipped.add(Map.of(USER_ID, userId, TYPE, notificationType,
                        SUB_CATEGORY, request.get(SUB_CATEGORY), REASON, NOTIFICATION_TYPE_DISABLED));
                continue;
            }
            buildRecordsForRequest(request, userId, notificationType, eligibleNotifications, actionRecords, failures);
        }
        log.info("Built {} notification records, {} skipped, {} failures",
                eligibleNotifications.size(), skipped.size(), failures.size());
    }

    /**
     * Returns {@code true} when the user has an explicit disabled setting for the given notification type.
     */
    private boolean isNotificationDisabled(
            Map<String, Map<String, NotificationSettingEntity>> userSettingsMap,
            String userId, String notificationType) {
        return Optional.ofNullable(userSettingsMap.get(userId))
                .map(userSettings -> userSettings.get(notificationType))
                .map(setting -> !setting.isEnabled())
                .orElse(false);
    }

    /**
     * Builds one notification record and one action record for a single user request.
     * On failure, adds an error entry to {@code failures} instead of propagating the exception.
     */
    private void buildRecordsForRequest(
            Map<String, Object> request, String userId, String notificationType,
            List<Map<String, Object>> eligibleNotifications,
            List<Map<String, Object>> actionRecords,
            List<Map<String, Object>> failures) {
        try {
            Map<String, Object> message = (Map<String, Object>) request.get(MESSAGE);
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) message.get(DATA);
            Map<String, Object> surveyData = dataList.get(0);
            Instant now = Instant.now();
            String notificationId = java.util.UUID.randomUUID().toString();

            Map<String, Object> notificationRecord = buildNotificationRecord(
                    notificationId, userId, notificationType, request, now);
            Map<String, Object> actionRecord = buildActionRecord(
                    notificationId, userId, request, surveyData, now);

            eligibleNotifications.add(notificationRecord);
            actionRecords.add(actionRecord);
        } catch (Exception e) {
            log.error("Failed to build records for user '{}': {}", userId, e.getMessage(), e);
            failures.add(Map.of(USER_ID, userId,
                    ERROR_KEY, ObjectUtils.defaultIfNull(e.getMessage(), e.getClass().getSimpleName())));
        }
    }

    /**
     * Constructs the Cassandra-ready notification map, serializing the {@code message}
     * field to a JSON string when it is not already a string.
     */
    private Map<String, Object> buildNotificationRecord(
            String notificationId, String userId, String notificationType,
            Map<String, Object> request, Instant now)
            throws JsonProcessingException {
        Map<String, Object> notificationMap = new HashMap<>();
        notificationMap.put(NOTIFICATION_ID, notificationId);
        notificationMap.put(USER_ID, userId);
        notificationMap.put(TYPE, notificationType);
        notificationMap.put(CATEGORY, request.get(CATEGORY));
        notificationMap.put(SUB_TYPE, request.get(SUB_TYPE));
        notificationMap.put(SOURCE, request.get(SOURCE));
        notificationMap.put(SUB_CATEGORY, request.get(SUB_CATEGORY));
        Object messageObj = request.get(MESSAGE);
        if (messageObj instanceof String) {
            notificationMap.put(MESSAGE, messageObj);
        } else if (messageObj != null) {
            notificationMap.put(MESSAGE, objectMapper.writeValueAsString(messageObj));
        }
        notificationMap.put(CREATED_AT, now);
        notificationMap.put(IS_DELETED, false);
        notificationMap.put(READ, false);
        notificationMap.put(READ_AT, null);
        return notificationMap;
    }

    /**
     * Constructs the peer-validation action record, parsing the survey end-date and
     * serializing survey metadata to JSON for Cassandra storage.
     */
    private Map<String, Object> buildActionRecord(
            String notificationId, String userId, Map<String, Object> request,
            Map<String, Object> surveyData, Instant now)
            throws JsonProcessingException {
        Instant surveyEndDate = Instant.parse((String) surveyData.get(SURVEY_END_DATE_KEY));
        Map<String, Object> actionMap = new HashMap<>();
        actionMap.put(NOTIFICATION_ID, notificationId);
        actionMap.put(USER_ID, userId);
        actionMap.put(SUB_CATEGORY, request.get(SUB_CATEGORY));
        actionMap.put(SURVEY_END_DATE, surveyEndDate);
        actionMap.put(ACTION_AT, null);
        actionMap.put(METADATA, objectMapper.writeValueAsString(surveyData));
        actionMap.put(CREATED_AT, now);

        return actionMap;
    }

    /**
     * Loads notification settings for all distinct user-IDs and notification types present in
     * the request list, returning them indexed as {@code userId → notificationType → setting}.
     */
    private Map<String, Map<String, NotificationSettingEntity>> fetchUserNotificationSettings(
            List<Map<String, Object>> requestList) {
        Set<String> distinctUserIds = new HashSet<>();
        Set<String> distinctNotificationTypes = new HashSet<>();
        for (Map<String, Object> req : requestList) {
            distinctUserIds.add((String) req.get(USER_ID));
            distinctNotificationTypes.add((String) req.get(TYPE));
        }
        List<NotificationSettingEntity> allSettings = notificationSettingRepository
                .findByUserIdInAndNotificationTypeInAndIsDeletedFalse(
                        new ArrayList<>(distinctUserIds), new ArrayList<>(distinctNotificationTypes));
        return allSettings.stream().collect(Collectors.groupingBy(
                NotificationSettingEntity::getUserId,
                Collectors.toMap(NotificationSettingEntity::getNotificationType, s -> s)));
    }


    /**
     * Validates the request list for nullability, size limits, and per-entry field constraints.
     *
     * @return an error message string if invalid, or {@code null} if valid
     */
    private String validatePeerValidationRequest(List<Map<String, Object>> requestList) {
        if (CollectionUtils.isEmpty(requestList)) {
            log.warn(INVALID_REQUEST_ERR_MSG, requestList);
            return INVALID_PAYLOAD_ERR_MSG;
        }
        int limit = cbServerProperties.getPeerValidationBulkUserNotificationLimit();
        if (requestList.size() > limit) {
            log.warn("Too many notifications in request: {}", requestList.size());
            return String.format(ERR_TOO_MANY_USERS_FMT, limit);
        }
        for (Map<String, Object> notificationRequest : requestList) {
            String error = validateSingleNotificationRequest(notificationRequest);
            if (StringUtils.isNotBlank(error)) {
                return error;
            }
        }
        return null;
    }

    /**
     * Validates all required fields of a single notification request entry, including
     * enum membership for category/sub-category and ISO-8601 format for survey end-date.
     *
     * @return an error message string if a field is missing or invalid, otherwise {@code null}
     */
    private String validateSingleNotificationRequest(Map<String, Object> request) {
        String userId = Objects.toString(request.get(USER_ID), null);
        if (StringUtils.isBlank(userId)) return ERR_USER_ID_REQUIRED;

        String type = Objects.toString(request.get(TYPE), null);
        if (StringUtils.isBlank(type)) return ERR_TYPE_REQUIRED;

        String categoryStr = Objects.toString(request.get(CATEGORY), null);
        if (StringUtils.isBlank(categoryStr)) return ERR_CATEGORY_REQUIRED;
        if (!EnumUtils.isValidEnum(NotificationCategory.class, categoryStr)) {
            return String.format(ERR_INVALID_CATEGORY_FMT, categoryStr);
        }

        String subCategoryStr = Objects.toString(request.get(SUB_CATEGORY), null);
        if (StringUtils.isBlank(subCategoryStr)) return ERR_SUB_CATEGORY_REQUIRED;
        if (!EnumUtils.isValidEnum(NotificationSubCategory.class, subCategoryStr)) {
            return String.format(ERR_INVALID_SUB_CATEGORY_FMT, subCategoryStr);
        }

        String subType = Objects.toString(request.get(SUB_TYPE), null);
        if (StringUtils.isBlank(subType)) return ERR_SUB_TYPE_REQUIRED;

        String source = Objects.toString(request.get(SOURCE), null);
        if (StringUtils.isBlank(source)) return ERR_SOURCE_REQUIRED;

        Object messageObj = request.get(MESSAGE);
        if (!(messageObj instanceof Map)) return ERR_MESSAGE_REQUIRED;
        Map<String, Object> message = (Map<String, Object>) messageObj;

        Object dataObj = message.get(DATA);
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) return ERR_MESSAGE_DATA_REQUIRED;
        Object firstEntry = dataList.get(0);
        if (!(firstEntry instanceof Map)) return ERR_MESSAGE_DATA_REQUIRED;
        Map<String, Object> surveyData = (Map<String, Object>) firstEntry;

        String surveyEndDateStr = Objects.toString(surveyData.get(SURVEY_END_DATE_KEY), null);
        if (StringUtils.isBlank(surveyEndDateStr)) return ERR_SURVEY_END_DATE_REQUIRED;
        try {
            Instant.parse(surveyEndDateStr);
        } catch (Exception e) {
            log.warn("Invalid surveyEndDate format: {}", surveyEndDateStr);
            return ERR_SURVEY_END_DATE_FORMAT;
        }
        return null;
    }
}
