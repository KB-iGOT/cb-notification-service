package com.igot.cb.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.notification.service.NotificationService;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.igot.cb.util.Constants.*;


@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {


    @Autowired
    AccessTokenValidator accessTokenValidator;

    @Autowired
    CassandraOperation cassandraOperation;

    @Autowired
    private ObjectMapper objectMapper;

    private final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Override
    public ApiResponse createNotification(JsonNode userNotificationDetail, String authToken) {
        log.info("NotificationService::createNotification: inside the method");
        ApiResponse outgoingResponse = ProjectUtil.createDefaultResponse(Constants.USER_NOTIFICATION_CREATE);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(outgoingResponse, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
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

            JsonNode requestNode = userNotificationDetail.get(Constants.REQUEST);
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
                log.warn("Missing or invalid 'request' node: {}", userNotificationDetail.toString());
                outgoingResponse.getParams().setErrMsg("Missing or invalid 'request' node in payload");
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
        ApiResponse outgoingResponse = ProjectUtil.createDefaultResponse(Constants.USER_NOTIFICATION_BULK_CREATE);

        try {
            JsonNode requestNode = userNotificationDetail.get(Constants.REQUEST);
            if (ObjectUtils.isEmpty(requestNode) || !requestNode.isObject()) {
                log.warn("Missing or invalid 'request' node: {}", userNotificationDetail.toString());
                updateErrorDetails(outgoingResponse, "Missing or invalid 'request' node in payload", HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            JsonNode userIdsNode = requestNode.get(USER_IDS);
            if (ObjectUtils.isEmpty(userIdsNode) || !userIdsNode.isArray()) {
                log.warn("Missing or invalid 'user_ids' in request");
                updateErrorDetails(outgoingResponse, "'user_ids' must be a non-empty list", HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            if (userIdsNode.size() > MAX_USER_LIMIT) {
                log.warn("Too many user_ids in request: {}", userIdsNode.size());
                updateErrorDetails(outgoingResponse, "Cannot send notifications to more than 100 users in a single request", HttpStatus.BAD_REQUEST);
                return outgoingResponse;
            }

            List<Map<String, Object>> notificationRecords = new ArrayList<>();
            ZoneId zoneId = ZoneId.of(UTC);
            Instant instant = LocalDateTime.now().atZone(zoneId).toInstant();

            for (JsonNode userIdNode : userIdsNode) {
                String userId = userIdNode.asText();
                if (StringUtils.isEmpty(userId)) {
                    log.warn("Empty user_id encountered in request");
                    continue;
                }

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

                notificationRecords.add(dbMap);
            }

            Object insertResponse = cassandraOperation.insertBulkRecord(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_NOTIFICATION,
                    notificationRecords
            );


            if (insertResponse instanceof ApiResponse apiResponse &&
                    Constants.FAILED.equals(apiResponse.get(Constants.RESPONSE))) {
                log.error("Bulk notification insertion failed: {}", apiResponse.getParams().getErrMsg());
                updateErrorDetails(outgoingResponse, "Failed to insert notifications", HttpStatus.INTERNAL_SERVER_ERROR);
                return outgoingResponse;
            }

            List<Map<String, Object>> responseList = notificationRecords.stream()
                    .map(this::prepareNotificationResponse)
                    .toList();

            outgoingResponse.setResponseCode(HttpStatus.OK);
            outgoingResponse.setResult(Map.of("notifications", responseList));
            log.info("NotificationService::bulkCreateNotification: Successfully inserted {} notifications", responseList.size());

        } catch (Exception e) {
            log.error("Error during bulk notification creation: {}", e.getMessage(), e);
            updateErrorDetails(outgoingResponse, "Internal server error while saving notifications", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return outgoingResponse;
    }


    @Override
    public ApiResponse readByUserIdAndNotificationId(String notificationId, String authToken) {
        log.info("NotificationService::readByUserIdAndNotificationId: inside the method");
        ApiResponse outgoingResponse = ProjectUtil.createDefaultResponse(Constants.USER_NOTIFICATION_READ_NOTIFICATIONID);

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
    public ApiResponse getNotificationsByUserIdAndLastXDays(String authToken, int days, int page, int size, NotificationReadStatus status, String categoryFilter) {
        log.info("NotificationService::readByUserIdAndLastXDaysNotifications: inside the method");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.USER_NOTIFICATION_READ_N_DAYSID);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return response;
            }

            Instant fromDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toInstant();

            List<Map<String, Object>> allNotifications = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_NOTIFICATION,
                    Map.of(USER_ID, userId),
                    List.of(NOTIFICATION_ID, CREATED_AT, TYPE, MESSAGE, READ, ROLE, SOURCE, CATEGORY),
                    MAX_NOTIFICATIONS_FETCH_FOR_READ
            );

            List<Map<String, Object>> statFiltered = allNotifications.stream()
                    .filter(notification -> {
                        Instant createdAt = (Instant) notification.get(CREATED_AT);
                        if (createdAt == null || createdAt.isBefore(fromDate)) return false;

                        Boolean isRead = (Boolean) notification.get(READ);
                        if (status == NotificationReadStatus.READ && !Boolean.TRUE.equals(isRead)) return false;
                        if (status == NotificationReadStatus.UNREAD && !Boolean.FALSE.equals(isRead)) return false;

                        return true;
                    })
                    .toList();


            Map<String, Map<String, Integer>> categoryCountMap = new HashMap<>();
            for (Map<String, Object> notification : statFiltered) {
                String cat = (String) notification.getOrDefault(CATEGORY, ALL);
                Boolean isRead = (Boolean) notification.get(READ);

                Map<String, Integer> counts = categoryCountMap.computeIfAbsent(cat, k -> new HashMap<>());
                counts.put(READ, counts.getOrDefault(READ, 0) + (Boolean.TRUE.equals(isRead) ? 1 : 0));
                counts.put(UNREAD, counts.getOrDefault(UNREAD, 0) + (Boolean.FALSE.equals(isRead) ? 1 : 0));
            }

            List<Map<String, Object>> categoryStats = categoryCountMap.entrySet().stream()
                    .map(this::buildCategoryStat)
                    .toList();


            List<Map<String, Object>> finalFiltered = statFiltered.stream()
                    .filter(notification -> {
                        if (StringUtils.isNotBlank(categoryFilter)) {
                            String cat = (String) notification.getOrDefault(CATEGORY, ALL);
                            return categoryFilter.equalsIgnoreCase(cat);
                        }
                        return true;
                    })
                    .toList();


            int total = finalFiltered.size();
            int fromIndex = Math.min(page * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<Map<String, Object>> paginated = finalFiltered.subList(fromIndex, toIndex);

            List<Map<String, Object>> processed = paginated.stream()
                    .map(this::prepareNotificationResponse)
                    .toList();

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(NOTIFICATIONS, processed);
            resultMap.put(TOTAL_COUNT, statFiltered.size());
            resultMap.put(PAGE, page);
            resultMap.put(SIZE, size);
            resultMap.put(HAS_NEXT_PAGE, toIndex < total);
            resultMap.put(CATEGORY_STATS, categoryStats);


            response.setResponseCode(HttpStatus.OK);
            response.setResult(resultMap);
            log.info("NotificationServiceImpl::readByUserIdAndLastXDaysNotifications: list retrieved successfully");

        } catch (Exception e) {
            log.error("Error while fetching readByUserIdAndLastXDaysNotifications from Cassandra: {}", e.getMessage(), e);
            updateErrorDetails(response,
                    "Internal server error while fetching readByUserIdAndLastXDaysNotifications list",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }


    private Map<String, Object> buildCategoryStat(Map.Entry<String, Map<String, Integer>> entry) {
        Map<String, Object> stat = new HashMap<>();
        stat.put(CATEGORY, entry.getKey());
        stat.put(READ, entry.getValue().getOrDefault(READ, 0));
        stat.put(UNREAD, entry.getValue().getOrDefault(UNREAD, 0));
        return stat;
    }

    @Override
    public ApiResponse markNotificationsAsRead(String authToken, Map<String, Object> request) {
        log.info("NotificationService::markNotificationsAsRead - Incoming request: {}", request);

        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.USER_NOTIFICATION_READ_UPDATEID);
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

        try {
            List<Map<String, Object>> userNotifications = fetchNotifications(userId);
            List<String> notificationIds;

            if (ALL.equalsIgnoreCase(type)) {
                notificationIds = userNotifications.stream()
                        .map(n -> (String) n.get(NOTIFICATION_ID))
                        .collect(Collectors.toList());
            } else if (INDIVIDUAL.equalsIgnoreCase(type)) {
                notificationIds = extractIndividualNotificationIds(request, response);
                if (notificationIds == null) return response;
            } else {
                updateErrorDetails(response, "Invalid type. Allowed values: all, individual", HttpStatus.BAD_REQUEST);
                return response;
            }

            List<Map<String, Object>> updated = processReadUpdate(userId, userNotifications, notificationIds);

            response.getParams().setErrMsg("Notifications updated successfully");
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResponseCode(HttpStatus.OK);
            response.setResult(Map.of("notifications", updated));

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
            return null;
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

    @Override
    public ApiResponse markNotificationsAsDeleted(String authToken, List<String> notificationIds) {
        log.info("NotificationService::markNotificationsAsDeleted - ids: {}", notificationIds);

        ApiResponse outgoingResponse = ProjectUtil.createDefaultResponse(Constants.USER_NOTIFICATION_DELETE);
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
        ApiResponse response = ProjectUtil.createDefaultResponse(USEE_NOTIFICATION_UNREAD_COUNT);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(authToken);
            if (StringUtils.isEmpty(userId)) {
                updateErrorDetails(response, Constants.USER_ID_DOESNT_EXIST, HttpStatus.BAD_REQUEST);
                return response;
            }

            ApiResponse daysValidationResponse = validateDays(days);
            if (ObjectUtils.isNotEmpty(daysValidationResponse)) {
                return daysValidationResponse;
            }

            Instant fromDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toInstant();

            List<Map<String, Object>> notificationRecords = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    Constants.KEYSPACE_SUNBIRD,
                    Constants.TABLE_USER_NOTIFICATION,
                    Map.of(USER_ID, userId),
                    List.of(CREATED_AT, READ),
                    MAX_NOTIFICATIONS_FETCH_FOR_READ
            );

            long unreadCount = notificationRecords.stream()
                    .filter(notificationRecord -> isUnreadAndWithinDateRange(notificationRecord, fromDate))
                    .count();

            response.setResponseCode(HttpStatus.OK);
            response.setResult(Map.of("unread", unreadCount));

        } catch (Exception e) {
            log.error("Error in getUnreadNotificationCount: {}", e.getMessage(), e);
            updateErrorDetails(response,
                    "Internal server error while fetching unread notification count",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    private boolean isUnreadAndWithinDateRange(Map<String, Object> notificationRecord, Instant fromDate) {
        if (notificationRecord == null) return false;

        Optional<Instant> createdAtOpt = parseCreatedAt(notificationRecord.get(CREATED_AT));
        if (createdAtOpt.isEmpty() || createdAtOpt.get().isBefore(fromDate)) {
            return false;
        }

        Object readFlag = notificationRecord.get(READ);
        return Boolean.FALSE.equals(readFlag);
    }

    private Optional<Instant> parseCreatedAt(Object createdAtObj) {
        if (createdAtObj instanceof Instant instant) {
            return Optional.of(instant);
        }

        if (createdAtObj instanceof String createdAtStr) {
            try {
                return Optional.of(Instant.parse(createdAtStr));
            } catch (DateTimeParseException e) {
                log.debug("Invalid CREATED_AT string format: {}", createdAtStr, e);
            }
        } else if (createdAtObj != null) {
            log.debug("Unexpected CREATED_AT type: {}", createdAtObj);
        }

        return Optional.empty();
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

        return cassandraOperation.getRecordsByPropertiesWithoutFiltering(
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

            return cassandraOperation.updateRecordByCompositeKey(
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
        Object messageObj = resultMap.get("message");

        if (messageObj instanceof String && messageObj != null) {
            try {
                JsonNode parsed = objectMapper.readTree((String) messageObj);
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

}
