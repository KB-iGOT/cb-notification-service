package com.igot.cb.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.notification.enums.NotificationSubCategory;
import com.igot.cb.userNotificationSetting.entity.NotificationSettingEntity;
import com.igot.cb.userNotificationSetting.repository.NotificationSettingRepository;
import com.igot.cb.util.Constants;

import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;


import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.igot.cb.util.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {
    @Spy
    @InjectMocks
    private NotificationServiceImpl notificationService;
    private static final String AUTH_TOKEN = "test-auth-token";
    @Mock
    private AccessTokenValidator accessTokenValidator;
    private static final String NOTIFICATION_ID_1 = "notification-id-1";
    private static final String NOTIFICATION_ID_2 = "notification-id-2";

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private ObjectMapper objectMapper;
    private static final String CREATED_AT = "created_at";
    private static final String READ = "read";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateNotification_Success_WithComplexMessage() throws Exception{
        // Prepare input
        String authToken = "Bearer token";
        String userId = "testUser";
        String notificationType = "comment";
        String category = "content";
        String source = "userCreated";
        String role = "user";


        String payload = "{ \"request\": { " +
                "\"type\": \"" + notificationType + "\"," +
                "\"category\": \"" + category + "\"," +
                "\"source\": \"" + source + "\"," +
                "\"role\": \"" + role + "\"," +
                "\"message\": { " +
                "\"topic\": \"subscriber-updates\"," +
                "\"notification\": { \"body\": \"This week's edition is now available.\", \"title\": \"NewsMagazine.com\" }," +
                "\"data\": { \"volume\": \"3.21.15\", \"contents\": \"http://www.news-magazine.com/world-week/21659772\" }" +
                "}" +
                "} }";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode userNotificationDetail = mapper.readTree(payload);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(userId, notificationType))
                .thenReturn(Optional.empty());
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("response", "SUCCESS"));

        doAnswer(invocation -> {
            String msg = invocation.getArgument(0, String.class);
            return mapper.readTree(msg);
        }).when(objectMapper).readTree(anyString());

        ApiResponse response = notificationService.createNotification(userNotificationDetail, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        Map<String, Object> result = (Map<String, Object>) response.getResult();

        assertFalse(result.containsKey(Constants.IS_DELETED));
        assertFalse(result.containsKey(Constants.UPDATED_AT));
        assertFalse(result.containsKey(Constants.USER_ID));
        assertFalse(result.containsKey(Constants.READ_AT));
        assertFalse(result.containsKey(Constants.TEMPLATE_ID));
        assertEquals(notificationType, result.get(Constants.TYPE));
        assertEquals(role, result.get(Constants.ROLE));
        assertEquals(source, result.get(Constants.SOURCE));
        assertEquals(category, result.get(Constants.CATEGORY));

        Object messageField = result.get(Constants.MESSAGE);
        assertNotNull(messageField, "MESSAGE field should not be null");
        assertTrue(messageField instanceof JsonNode);
        JsonNode messageNode = (JsonNode) messageField;
        assertEquals("subscriber-updates", messageNode.get("topic").asText());
        assertEquals("NewsMagazine.com", messageNode.get("notification").get("title").asText());
        assertEquals("3.21.15", messageNode.get("data").get("volume").asText());
    }

    @Test
    void testCreateNotification_MissingUserId() throws Exception{
        String authToken = "Bearer token";
        ObjectMapper mapper = new ObjectMapper();
        String payload = "{ \"request\": { \"type\": \"comment\" } }";
        JsonNode userNotificationDetail = mapper.readTree(payload);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");

        ApiResponse response = notificationService.createNotification(userNotificationDetail, authToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testCreateNotification_MissingRequestNode() throws Exception {
        String authToken = "Bearer token";
        String userId = "testUser";
        ObjectMapper mapper = new ObjectMapper();
        String payload = "{}";
        JsonNode userNotificationDetail = mapper.readTree(payload);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);

        ApiResponse response = notificationService.createNotification(userNotificationDetail, authToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Missing or invalid 'request' node in payload", response.getParams().getErrMsg());
    }

    @Test
    void testCreateNotification_MessageStringParsingFails() throws Exception {
        // This test simulates when the message field is a string but not valid JSON
        String authToken = "Bearer token";
        String userId = "testUser";
        String notificationType = "comment";
        String category = "content";
        String source = "userCreated";
        String role = "user";
        // message is a string, not a JSON object
        String payload = "{ \"request\": { " +
                "\"type\": \"" + notificationType + "\"," +
                "\"category\": \"" + category + "\"," +
                "\"source\": \"" + source + "\"," +
                "\"role\": \"" + role + "\"," +
                "\"message\": \"This is not JSON\"" +
                "} }";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode userNotificationDetail = mapper.readTree(payload);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("response", "SUCCESS"));
        doThrow(new RuntimeException("Not a JSON")).when(objectMapper).readTree("This is not JSON");

        ApiResponse response = notificationService.createNotification(userNotificationDetail, authToken);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals("This is not JSON", result.get(Constants.MESSAGE));
    }

    @Test
    void testMarkNotificationsAsDeleted_TooManyIds() {
        String authToken = "Bearer abc";
        String userId = "user-1";
        int batchSize = Constants.MAX_NOTIFICATION_READ_BATCH_SIZE + 1;
        List<String> notificationIds = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) notificationIds.add("id-" + i);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);

        ApiResponse response = notificationService.markNotificationsAsDeleted(authToken, notificationIds);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("only mark up to"));
    }

    @Test
    void testPrepareNotificationResponse_MessageIsNonString() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put("message", 12345); // not a String

        // objectMapper.readTree should not be called
        Map<String, Object> result = notificationService.prepareNotificationResponse(dbRecord);
        assertEquals(12345, result.get("message"));
    }

    @Test
    void testPrepareNotificationResponse_MessageIsNull() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put("message", null);

        Map<String, Object> result = notificationService.prepareNotificationResponse(dbRecord);
        assertNull(result.get("message"));
    }

    @Test
    void testBulkCreateNotifications_exception() throws Exception {
        String payload = "{ \"request\": { " +
                "\"type\": \"comment\"," +
                "\"user_ids\": [ {\"user_id\": \"user1\"} ]" +
                "} }";
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode userNotificationDetail = realMapper.readTree(payload);

        when(cassandraOperation.insertBulkRecord(
                anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = notificationService.bulkCreateNotifications(userNotificationDetail);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Internal server error while saving notifications", response.getParams().getErrMsg());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testBulkCreateNotifications_success() throws Exception {
        String payload = "{ \"request\": { " +
                "\"type\": \"comment\"," +
                "\"category\": \"content\"," +
                "\"sub_category\": \"CONTENT_PUBLISHED\"," +
                "\"message\": {\"title\": \"Test\", \"data\": {\"foo\": \"bar\"}}," +
                "\"user_ids\": [ {\"user_id\": \"user1\"}, {\"user_id\": \"user2\"} ]" +
                "} }";
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode userNotificationDetail = realMapper.readTree(payload);

        NotificationSettingEntity entity = new NotificationSettingEntity();
        entity.setEnabled(true); // or entity.setIsEnabled(true); based on your class
        when(notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(entity));

        ApiResponse mockInsertResponse = new ApiResponse();
        mockInsertResponse.setResponseCode(HttpStatus.OK);
        mockInsertResponse.setResult(Map.of(Constants.RESPONSE, Constants.SUCCESS));
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), anyList()))
                .thenReturn(mockInsertResponse);

        // Spy to pass through prepareNotificationResponse
        NotificationServiceImpl spyService = Mockito.spy(notificationService);
        doAnswer(invocation -> invocation.getArgument(0))  // Simply return the input
                .when(spyService).prepareNotificationResponse(any());

        ApiResponse response = spyService.bulkCreateNotifications(userNotificationDetail);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("notifications"));

        List<Map<String, Object>> notifications = (List<Map<String, Object>>) result.get("notifications");
        assertEquals(2, notifications.size());

        Set<String> userIds = notifications.stream()
                .map(n -> (String) n.get(Constants.USER_ID))
                .collect(Collectors.toSet());

        assertTrue(userIds.contains("user1"));
        assertTrue(userIds.contains("user2"));
    }


    @Test
    void testBulkCreateNotifications_missingRequest() throws Exception {
        String payload = "{}";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode userNotificationDetail = mapper.readTree(payload);

        ApiResponse response = notificationService.bulkCreateNotifications(userNotificationDetail);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Missing or invalid 'request' node in payload", response.getParams().getErrMsg());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testBulkCreateNotifications_missingUserIds() throws Exception {
        String payload = "{ \"request\": { \"type\": \"comment\" } }";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode userNotificationDetail = mapper.readTree(payload);

        ApiResponse response = notificationService.bulkCreateNotifications(userNotificationDetail);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("'user_ids' must be a non-empty list", response.getParams().getErrMsg());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testBulkCreateNotifications_tooManyUserIds() throws Exception {
        // Build user_ids array > MAX_USER_LIMIT (100)
        int limit = 101;
        StringBuilder userIdsJson = new StringBuilder("[");
        for (int i = 0; i < limit; i++) {
            if (i > 0) userIdsJson.append(",");
            userIdsJson.append("\"user").append(i).append("\"");
        }
        userIdsJson.append("]");
        String payload = "{ \"request\": { \"type\": \"comment\", \"user_ids\": " + userIdsJson + " } }";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode userNotificationDetail = mapper.readTree(payload);

        ApiResponse response = notificationService.bulkCreateNotifications(userNotificationDetail);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Cannot send notifications to more than 100 users"));
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_filterUnread() {
        String authToken = "Bearer xyz";
        String userId = "user-42";
        int days = 10, page = 0, size = 10;
        Instant now = Instant.now();

        Map<String, Object> notif1 = new HashMap<>();
        notif1.put(NOTIFICATION_ID, "n1");
        notif1.put(Constants.USER_ID, userId);
        notif1.put(Constants.CREATED_AT, now.minusSeconds(3600));
        notif1.put(Constants.READ, false);
        notif1.put(Constants.CATEGORY, "catA");

        Map<String, Object> notif2 = new HashMap<>();
        notif2.put(NOTIFICATION_ID, "n2");
        notif2.put(Constants.USER_ID, userId);
        notif2.put(Constants.CREATED_AT, now.minusSeconds(7200));
        notif2.put(Constants.READ, true);
        notif2.put(Constants.CATEGORY, "catA");

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);

        // Correctly mock user and global notification calls separately
        when(cassandraOperation.getRecordsByProperties(
                anyString(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif1, notif2));

        when(cassandraOperation.getRecordsByProperties(
                anyString(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of()); // empty global notifications

        NotificationServiceImpl spyService = Mockito.spy(notificationService);
        doAnswer(invocation -> invocation.getArgument(0)).when(spyService).prepareNotificationResponse(any());

        ApiResponse response = spyService.getNotificationsByUserIdAndLastXDays(
                authToken, days, page, size, NotificationReadStatus.UNREAD, null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<Map<String, Object>> returnedNotifs = (List<Map<String, Object>>) result.get(NOTIFICATIONS);
        assertEquals(1, returnedNotifs.size());
        assertEquals("n1", returnedNotifs.get(0).get(NOTIFICATION_ID));
    }
    private boolean isGlobalSubCategory(NotificationSubCategory subCategory) {
        return NotificationSubCategory.EVENT_PUBLISHED.equals(subCategory) ||
                NotificationSubCategory.COURSE_PUBLISHED.equals(subCategory) ||
                NotificationSubCategory.PROGRAM_PUBLISHED.equals(subCategory);
    }

    @Test
    public void testIsGlobalSubCategory() {
        assertTrue(isGlobalSubCategory(NotificationSubCategory.EVENT_PUBLISHED));
        assertTrue(isGlobalSubCategory(NotificationSubCategory.COURSE_PUBLISHED));
        assertTrue(isGlobalSubCategory(NotificationSubCategory.PROGRAM_PUBLISHED));
        assertFalse(isGlobalSubCategory(null));
    }

    @Test
    void testGetInstant_withInstant() {
        Instant now = Instant.now();
        Instant result = notificationService.getInstant(now);
        assertEquals(now, result);
    }

    @Test
    void testGetInstant_withDate() {
        Date date = new Date();
        Instant expected = date.toInstant();
        Instant result = notificationService.getInstant(date);
        assertEquals(expected, result);
    }

    @Test
    void testGetInstant_withValidString() {
        String validIsoString = "2023-08-07T10:15:30Z";
        Instant expected = Instant.parse(validIsoString);
        Instant result = notificationService.getInstant(validIsoString);
        assertEquals(expected, result);
    }

    @Test
    void testGetInstant_withInvalidString() {
        String invalidString = "not-a-date";
        Instant result = notificationService.getInstant(invalidString);
        assertNull(result);
    }

    @Test
    void testGetInstant_withUnsupportedType() {
        Integer unsupportedValue = 12345;
        Instant result = notificationService.getInstant(unsupportedValue);
        assertNull(result);
    }

    @Test
    void testCreateGlobalNotification_Success() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.EVENT_PUBLISHED;

        String jsonPayload = "{ " +
                "\"type\": \"info\", " +
                "\"category\": \"event\", " +
                "\"sub_category\": \"EVENT_PUBLISHED\", " +
                "\"sub_type\": \"announcement\", " +
                "\"source\": \"system\", " +
                "\"role\": \"admin\", " +
                "\"template_id\": \"template123\", " +
                "\"message\": { \"title\": \"Event started\", \"body\": \"The event is live now!\" } " +
                "}";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode requestNode = mapper.readTree(jsonPayload);

        when(cassandraOperation.insertRecord(
                anyString(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap()))
                .thenReturn(Map.of("response", Constants.SUCCESS));

        NotificationServiceImpl spyService = Mockito.spy(notificationService);
        doAnswer(invocation -> invocation.getArgument(0))
                .when(spyService).prepareNotificationResponse(any());

        ApiResponse response = spyService.createGlobalNotification(subCategory, requestNode);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("notification"));

        Map<String, Object> notification = (Map<String, Object>) result.get("notification");

        assertEquals(Constants.GLOBAL, notification.get(Constants.USER_ID));

    }

    @Test
    void testMarkNotificationsAsRead_invalidType() {
        String authToken = "Bearer xyz";
        String userId = "user-42";
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.TYPE, "invalid");

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        ApiResponse response = notificationService.markNotificationsAsRead(authToken, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Invalid type"));
    }

    @Test
    void testMarkNotificationsAsRead_missingType() {
        String authToken = "Bearer xyz";
        String userId = "user-42";
        Map<String, Object> request = new HashMap<>();

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        ApiResponse response = notificationService.markNotificationsAsRead(authToken, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Request type must be provided"));
    }

    @Test
    void testMarkNotificationsAsRead_invalidIdsForIndividual() {
        String authToken = "Bearer xyz";
        String userId = "user-42";
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.TYPE, Constants.INDIVIDUAL);
        request.put("ids", "notalist"); // Invalid ids type

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        ApiResponse response = notificationService.markNotificationsAsRead(authToken, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErrMsg().contains("Missing or invalid 'ids' field"));
    }


    @Test
    void testGetUnreadNotificationCount_userIdMissing() {
        String authToken = "Bearer xyz";
        int days = 7;

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn("");

        ApiResponse response = notificationService.getUnreadNotificationCount(authToken, days);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testReadByUserIdAndNotificationId_Success() {
        ApiResponse expectedResponse = ApiResponse.createDefaultResponse(Constants.USER_NOTIFICATION_READ_NOTIFICATIONID);
        expectedResponse.setResponseCode(HttpStatus.OK);
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(USER_ID);
        ApiResponse actualResponse = notificationService.readByUserIdAndNotificationId(NOTIFICATION_ID, AUTH_TOKEN);
        assertNotNull(actualResponse);
        assertEquals(HttpStatus.OK, actualResponse.getResponseCode());
    }

    @Test
    void testReadByUserIdAndNotificationId_EmptyUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn("");
        ApiResponse response = notificationService.readByUserIdAndNotificationId(NOTIFICATION_ID, AUTH_TOKEN);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testReadByUserIdAndNotificationId_Exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenThrow(new RuntimeException("Test exception"));
        ApiResponse response = notificationService.readByUserIdAndNotificationId(NOTIFICATION_ID, AUTH_TOKEN);
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Internal server error while fetching notification by userId",
                response.getParams().getErrMsg());
    }

    @Test
    void testMarkNotificationsAsDeleted_Success() {
        List<String> notificationIds = Arrays.asList(NOTIFICATION_ID_1, NOTIFICATION_ID_2);
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(USER_ID);
        ApiResponse response = notificationService.markNotificationsAsDeleted(AUTH_TOKEN, notificationIds);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals("Notifications marked as deleted successfully", response.getParams().getErrMsg());

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updatedNotifications = (List<Map<String, Object>>) result.get(NOTIFICATIONS);
        assertNotNull(updatedNotifications);
    }

    @Test
    void testMarkNotificationsAsDeleted_EmptyUserId() {
        List<String> notificationIds = Arrays.asList(NOTIFICATION_ID_1, NOTIFICATION_ID_2);
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn("");
        ApiResponse response = notificationService.markNotificationsAsDeleted(AUTH_TOKEN, notificationIds);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testMarkNotificationsAsDeleted_EmptyNotificationIds() {
        List<String> notificationIds = Collections.emptyList();
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(USER_ID);
        ApiResponse response = notificationService.markNotificationsAsDeleted(AUTH_TOKEN, notificationIds);
        assertNotNull(response);
        assertNotEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testMarkNotificationsAsDeleted_NullNotificationIds() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(USER_ID);
        ApiResponse response = notificationService.markNotificationsAsDeleted(AUTH_TOKEN, null);
        assertNotNull(response);
        assertNotEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testMarkNotificationsAsDeleted_Exception() {
        List<String> notificationIds = Arrays.asList(NOTIFICATION_ID_1, NOTIFICATION_ID_2);
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenThrow(new RuntimeException("Test exception"));
        ApiResponse response = notificationService.markNotificationsAsDeleted(AUTH_TOKEN, notificationIds);
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Internal server error while fetching markNotificationsAsDeleted  delete",
                response.getParams().getErrMsg());
    }


    @Test
    void testGetUnreadNotificationCount_EmptyUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn("");
        ApiResponse response = notificationService.getUnreadNotificationCount(AUTH_TOKEN, 7);
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }


    @Test
    void testGetUnreadNotificationCount_InvalidDays() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(USER_ID);
        ApiResponse response = notificationService.getUnreadNotificationCount(AUTH_TOKEN, -1);
        assertNotNull(response);
        assertNotEquals(HttpStatus.OK, response.getResponseCode());
    }


    @Test
    void testProcessReadUpdate_AllScenarios() throws Exception {
        // Notification not found
        List<Map<String, Object>> notifications = List.of(
                Map.of(NOTIFICATION_ID, "not-matching-id", READ, false)
        );

        List<String> targetIds = List.of(NOTIFICATION_ID);

        // Use reflection to invoke private method
        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "processReadUpdate", String.class, List.class, List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(
                notificationService, USER_ID, notifications, targetIds);

        assertTrue(result.isEmpty());
    }

    @Test
    void testProcessReadUpdate_MarkUnreadAsRead_Success() throws Exception {
        Instant createdAt = Instant.now();
        Map<String, Object> notification = new HashMap<>();
        notification.put(NOTIFICATION_ID, NOTIFICATION_ID);
        notification.put(READ, false);
        notification.put(CREATED_AT, createdAt);

        List<Map<String, Object>> notifications = List.of(notification);
        List<String> targetIds = List.of(NOTIFICATION_ID);

        Map<String, Object> updateResponse = Map.of(Constants.RESPONSE, Constants.SUCCESS);

        when(cassandraOperation.updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_NOTIFICATION),
                anyMap(),
                anyMap()
        )).thenReturn(updateResponse);

        // Mock fetchNotifications
        Method fetchMethod = NotificationServiceImpl.class.getDeclaredMethod("fetchNotifications", String.class);
        fetchMethod.setAccessible(true);
        ReflectionTestUtils.setField(notificationService, "cassandraOperation", cassandraOperation);

        doReturn(List.of(notification)).when(cassandraOperation).getRecordsByProperties(
                any(), any(), anyMap(), any(), anyInt());

        // Invoke processReadUpdate
        Method processMethod = NotificationServiceImpl.class.getDeclaredMethod(
                "processReadUpdate", String.class, List.class, List.class);
        processMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) processMethod.invoke(
                notificationService, USER_ID, notifications, targetIds);

        assertEquals(1, result.size());
        assertEquals(NOTIFICATION_ID, result.get(0).get(ID));
    }

    @Test
    void testUpdateNotification_NotificationFound() throws Exception {
        Instant createdAt = Instant.now();
        Map<String, Object> notification = new HashMap<>();
        notification.put(NOTIFICATION_ID, NOTIFICATION_ID);
        notification.put(CREATED_AT, createdAt);

        List<Map<String, Object>> notifications = List.of(notification);

        when(cassandraOperation.getRecordsByProperties(
                any(), any(), anyMap(), any(), anyInt()
        )).thenReturn(notifications);

        when(cassandraOperation.updateRecord(
                any(), any(), anyMap(), anyMap()
        )).thenReturn(Map.of(Constants.RESPONSE, Constants.SUCCESS));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "updateNotification", String.class, String.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> updateMap = Map.of(READ, true, READ_AT, Instant.now());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(
                notificationService, USER_ID, NOTIFICATION_ID, updateMap);

        assertEquals(Constants.SUCCESS, result.get(Constants.RESPONSE));
    }

    @Test
    void testUpdateNotification_NotificationNotFound() throws Exception {
        when(cassandraOperation.getRecordsByProperties(
                any(), any(), anyMap(), any(), anyInt()
        )).thenReturn(Collections.emptyList());

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "updateNotification", String.class, String.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> updateMap = Map.of(READ, true, READ_AT, Instant.now());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(
                notificationService, USER_ID, NOTIFICATION_ID, updateMap);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchNotifications() throws Exception {
        Map<String, Object> notification = new HashMap<>();
        notification.put(USER_ID, USER_ID);
        notification.put(NOTIFICATION_ID, NOTIFICATION_ID);

        when(cassandraOperation.getRecordsByProperties(
                any(), any(), anyMap(), any(), anyInt()
        )).thenReturn(List.of(notification));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "fetchNotifications", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(
                notificationService, USER_ID);

        assertEquals(1, result.size());
        assertEquals(NOTIFICATION_ID, result.get(0).get(NOTIFICATION_ID));
    }

    @Test
    void testMarkNotificationsAsRead_InternalServerError() {
        // Mocked input
        String authToken = "Bearer token";
        String userId = "user123";
        String notificationId = "notif001";
        Instant createdAt = Instant.now();

        Map<String, Object> request = Map.of("type", "all");

        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", notificationId);
        notification.put("read", false);
        notification.put("createdAt", createdAt);

        List<Map<String, Object>> notifications = List.of(notification);

        Map<String, Object> successUpdateResponse = Map.of("response", "SUCCESS");

        // Mocks
        Mockito.when(accessTokenValidator.fetchUserIdFromAccessToken(authToken))
                .thenReturn(userId);

        Mockito.when(cassandraOperation.getRecordsByProperties(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.TABLE_USER_NOTIFICATION),
                        anyMap(),
                        isNull(),
                        anyInt()))
                .thenReturn(notifications);

        Mockito.when(cassandraOperation.updateRecord(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.TABLE_USER_NOTIFICATION),
                        anyMap(),
                        anyMap()))
                .thenReturn(successUpdateResponse);

        // Call method
        ApiResponse response = notificationService.markNotificationsAsRead(authToken, request);

        // Assertions
        assertNotNull(response);
        assertEquals("Internal server error while updating notifications", response.getParams().getErrMsg());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testMarkNotificationsAsRead_InvalidUser() {
        // Mocked input
        String authToken = "Bearer token";
        String notificationId = "notif001";
        Instant createdAt = Instant.now();

        Map<String, Object> request = Map.of("type", "all");

        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", notificationId);
        notification.put("read", false);
        notification.put("createdAt", createdAt);

        List<Map<String, Object>> notifications = List.of(notification);

        Map<String, Object> successUpdateResponse = Map.of("response", "SUCCESS");

        // Mocks
        Mockito.when(accessTokenValidator.fetchUserIdFromAccessToken(authToken))
                .thenReturn(null);

        Mockito.when(cassandraOperation.getRecordsByProperties(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.TABLE_USER_NOTIFICATION),
                        anyMap(),
                        isNull(),
                        anyInt()))
                .thenReturn(notifications);

        Mockito.when(cassandraOperation.updateRecord(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.TABLE_USER_NOTIFICATION),
                        anyMap(),
                        anyMap()))
                .thenReturn(successUpdateResponse);

        // Call method
        ApiResponse response = notificationService.markNotificationsAsRead(authToken, request);

        // Assertions
        assertNotNull(response);
        assertEquals("User Id doesn't exist! Please supply a valid auth token", response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetUnreadNotificationCount_success_withExistingRecord() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(USER_ID);

        Map<String, Object> record = Map.of(Constants.COUNT, 10);
        when(cassandraOperation.getRecordsByProperties(
                anyString(), anyString(), anyMap(), anyList(), eq(1)))
                .thenReturn(List.of(record));

        ApiResponse response = notificationService.getUnreadNotificationCount(AUTH_TOKEN, 5);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        assertEquals(10, ((Map<?, ?>) response.getResult()).get("unread"));
    }


    @Test
    void testGetUnreadNotificationCount_badRequest_whenUserIdMissing() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn("");

        ApiResponse response = notificationService.getUnreadNotificationCount(AUTH_TOKEN, 5);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testGetUnreadNotificationCount_internalServerError_onException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenThrow(new RuntimeException("DB failure"));

        ApiResponse response = notificationService.getUnreadNotificationCount(AUTH_TOKEN, 5);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetResetNotificationCount_success() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(USER_ID);

        when(cassandraOperation.updateRecord(
                anyString(), anyString(), anyMap(), anyMap()))
                .thenReturn(Map.of(Constants.RESPONSE, Constants.SUCCESS));

        ApiResponse response = notificationService.getResetNotificationCount(AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testGetResetNotificationCount_badRequest_whenUserIdMissing() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn("");

        ApiResponse response = notificationService.getResetNotificationCount(AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testGetResetNotificationCount_internalServerError_onException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenThrow(new RuntimeException("DB error"));

        ApiResponse response = notificationService.getResetNotificationCount(AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testClubNotification_InsertsNewNotification_WhenNoExistingClubFound() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        String userId = "user123";
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.readTree("{\"message\":{\"data\":{\"discussionId\":\"d1\"}}}");

        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap()))
                .thenReturn(Map.of("response", "SUCCESS"));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, subCategory, userId, requestNode);

        verify(cassandraOperation, times(1))
                .insertRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_NOTIFICATION), anyMap());
    }

    @Test
    void testClubNotification_UpdatesExistingNotification_WhenWithinClubWindowAndSameClubKey() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        String userId = "user123";
        Instant createdAt = Instant.now();

        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.readTree("{\"message\":{\"data\":{\"discussionId\":\"d1\"}}}");
        JsonNode existingMessage = realMapper.readTree("{\"data\":{\"discussionId\":\"d1\",\"count\":1}}");

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.USER_ID, userId);
        dbRecord.put(Constants.SUB_CATEGORY, subCategory.name());
        dbRecord.put(Constants.CREATED_AT, createdAt);
        dbRecord.put(Constants.MESSAGE, existingMessage.toString());

        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));
        when(objectMapper.readTree(anyString())).thenReturn(existingMessage);
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenReturn(existingMessage.toString());

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, subCategory, userId, requestNode);

        verify(cassandraOperation, times(1))
                .updateRecord(eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.TABLE_USER_NOTIFICATION),
                        anyMap(), anyMap());
    }

    @Test
    void testClubNotification_SkipsUpdate_WhenDifferentSubCategory() throws Exception {
        String userId = "user123";
        Instant createdAt = Instant.now();

        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.readTree("{\"message\":{\"data\":{\"discussionId\":\"d1\"}}}");
        JsonNode existingMessage = realMapper.readTree("{\"data\":{\"discussionId\":\"d2\",\"count\":1}}");

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.USER_ID, userId);
        dbRecord.put(Constants.SUB_CATEGORY, NotificationSubCategory.LIKED_COMMENT.name());
        dbRecord.put(Constants.CREATED_AT, createdAt);
        dbRecord.put(Constants.MESSAGE, existingMessage.toString());

        // mock Cassandra returning this record
        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));

        // mock ObjectMapper so it doesn’t return null
        when(objectMapper.readTree(existingMessage.toString())).thenReturn(existingMessage);
        when(objectMapper.readTree(requestNode.get("message").toString())).thenReturn(requestNode.get("message"));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, NotificationSubCategory.LIKED_POST, userId, requestNode);

        // since subcategories differ, update should never be called
        verify(cassandraOperation, never())
                .updateRecord(any(), any(), anyMap(), anyMap());
    }


    @Test
    void testUpdateNotificationMessage_IncrementsCountAndUpdatesBody() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode messageNode = mapper.readTree("{\"data\":{\"count\":1},\"body\":\"old\"}");

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "updateNotificationMessage", NotificationSubCategory.class, JsonNode.class);
        method.setAccessible(true);

        JsonNode updated = (JsonNode) method.invoke(notificationService, NotificationSubCategory.LIKED_POST, messageNode);

        assertEquals(2, updated.get("data").get("count").asInt());
        assertTrue(updated.get("body").asText().contains("2"));
    }

    @Test
    void testConstructMessage_ReplacesPlaceholderCorrectly() throws Exception {
        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "constructMessage", NotificationSubCategory.class, Map.class);
        method.setAccessible(true);

        String result = (String) method.invoke(notificationService,
                NotificationSubCategory.LIKED_POST, Map.of("count", "5"));

        assertTrue(result.contains("5 users liked your post"));
    }


    @Test
    void testConstructMessage_IgnoresMissingPlaceholder() throws Exception {
        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "constructMessage", NotificationSubCategory.class, Map.class);
        method.setAccessible(true);

        String result = (String) method.invoke(notificationService,
                NotificationSubCategory.LIKED_POST, Map.of());

        assertTrue(result.contains("{count}"));
    }


    @Test
    void testGetNotificationsByUserIdAndLastXDays_DisabledNotifications_ReturnsEmptyList() {
        String authToken = "Bearer abc";
        String userId = "u123";
        NotificationSettingEntity entity = new NotificationSettingEntity();
        entity.setEnabled(false);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(eq(userId), any()))
                .thenReturn(Optional.of(entity));

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals(0, result.get(Constants.TOTAL_COUNT));
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_FiltersDeletedNotifications() {
        String authToken = "Bearer abc";
        String userId = "u123";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n1");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, true);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertTrue(notifications.isEmpty());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_FiltersBySubType() {
        String authToken = "Bearer abc";
        String userId = "u123";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n1");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, false);
        notif.put(Constants.SUB_TYPE, "announcement");

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, "announcement");

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertEquals(1, notifications.size());
    }
    @Test
    void testGetNotificationsByUserIdAndLastXDays_HandlesException_ReturnsInternalServerError() {
        String authToken = "Bearer abc";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenThrow(new RuntimeException("DB error"));

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_FiltersReadNotifications() {
        String authToken = "Bearer xyz";
        String userId = "u456";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n2");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, true);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.UNREAD, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertTrue(notifications.isEmpty());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_ReturnsOnlyReadNotifications() {
        String authToken = "Bearer read";
        String userId = "u789";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n3");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, true);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.READ, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertEquals(1, notifications.size());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_NoSettingsFound_ReturnsNotifications() {
        String authToken = "Bearer missing";
        String userId = "u999";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n4");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, false);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertEquals(1, notifications.size());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_ThrowsOnCassandraError() {
        String authToken = "Bearer crash";
        String userId = "u111";

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), any(), anyMap(), any(), anyInt()))
                .thenThrow(new RuntimeException("Cassandra down"));

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_ReturnsGlobalNotifications() {
        String authToken = "Bearer global";
        String userId = "u112";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n5");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, false);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertEquals(1, notifications.size());
    }

    @Test
    void testClubNotification_ShouldInsertNewNotification_WhenNoExistingFound() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        String userId = "userNew";
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.readTree("{\"discussionId\":\"d1\"}");

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(objectMapper.writeValueAsString(any(JsonNode.class)))
                .thenReturn("{\"discussionId\":\"d1\"}");

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, subCategory, userId, requestNode);

        // Verify 3-arg insertRecord
        verify(cassandraOperation, times(1))
                .insertRecord(anyString(), anyString(), anyMap());
    }



    @Test
    void testGetNotificationsByUserIdAndLastXDays_PaginationWorks() {
        String authToken = "Bearer page";
        String userId = "u222";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n6");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, false);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 1, 10, NotificationReadStatus.BOTH, null); // offset=1

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals(0, ((List<?>) result.get(Constants.NOTIFICATIONS)).size());
    }


    @Test
    void testGetUnreadNotificationCount_countObjNotNumber() {
        String authToken = "Bearer token";
        String userId = "u123";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.COUNT, "not-a-number"); // invalid type

        when(cassandraOperation.getRecordsByProperties(
                anyString(), eq(Constants.TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), eq(1)))
                .thenReturn(List.of(record));

        ApiResponse response = notificationService.getUnreadNotificationCount(authToken, 7);
        Map<String, Object> result = (Map<String, Object>) response.getResult();

        assertEquals(0, result.get("unread"));
    }

    @Test
    void testGetUnreadNotificationCount_withLastUpdatedNull() {
        String authToken = "Bearer token";
        String userId = "u123";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.COUNT, 5);
        record.put(Constants.UPDATED_AT, null);

        when(cassandraOperation.getRecordsByProperties(
                anyString(), eq(Constants.TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), eq(1)))
                .thenReturn(List.of(record));

        ApiResponse response = notificationService.getUnreadNotificationCount(authToken, 7);
        Map<String, Object> result = (Map<String, Object>) response.getResult();

        assertEquals(5, result.get("unread"));
    }

    @Test
    void testGetUnreadNotificationCount_withLastUpdatedNotNullAndNoGlobalNotifs() {
        String authToken = "Bearer token";
        String userId = "u123";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);

        Instant lastUpdated = Instant.now();
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.COUNT, 2);
        record.put(Constants.UPDATED_AT, lastUpdated);

        when(cassandraOperation.getRecordsByProperties(
                anyString(), eq(Constants.TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), eq(1)))
                .thenReturn(List.of(record));
        when(cassandraOperation.getRecordsByProperties(
                anyString(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = notificationService.getUnreadNotificationCount(authToken, 7);
        Map<String, Object> result = (Map<String, Object>) response.getResult();

        assertEquals(2, result.get("unread"));
    }

// ---------- clubNotification ----------

    @Test
    void testClubNotification_SkipsWhenDifferentSubCategory() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        String userId = "user123";

        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.readTree("{\"message\":{\"data\":{\"discussionId\":\"d1\"}}}");

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.USER_ID, userId);
        dbRecord.put(Constants.SUB_CATEGORY, NotificationSubCategory.LIKED_COMMENT.name()); // different subCategory
        dbRecord.put(Constants.CREATED_AT, Instant.now());
        dbRecord.put(Constants.MESSAGE, "{\"data\":{\"discussionId\":\"d1\"}}");

        // ✅ Ensure readTree returns a real JsonNode instead of null
        when(objectMapper.readTree(anyString()))
                .thenAnswer(invocation -> realMapper.readTree((String) invocation.getArgument(0)));

        when(cassandraOperation.getRecordsByProperties(
                any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, subCategory, userId, requestNode);

        // ✅ Should skip update since subCategory mismatches
        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }


    @Test
    void testClubNotification_SkipsWhenClubWindowExpired() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        String userId = "user123";

        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.readTree("{\"message\":{\"data\":{\"discussionId\":\"d1\"}}}");

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.USER_ID, userId);
        dbRecord.put(Constants.SUB_CATEGORY, subCategory.name());
        dbRecord.put(Constants.CREATED_AT, Instant.now().minus(Duration.ofHours(1))); // expired
        dbRecord.put(Constants.MESSAGE, "{\"data\":{\"discussionId\":\"d1\"}}");

        // ✅ Ensure mock objectMapper delegates to real mapper
        when(objectMapper.readTree(anyString()))
                .thenAnswer(invocation -> realMapper.readTree((String) invocation.getArgument(0)));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, subCategory, userId, requestNode);

        // ✅ Since clubWindow expired, no update should happen
        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }


    @Test
    void testClubNotification_SkipsWhenUserMismatch() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        ObjectMapper realMapper = new ObjectMapper();

        JsonNode requestNode = realMapper.readTree("{\"message\":{\"data\":{\"discussionId\":\"d1\"}}}");

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.USER_ID, "otherUser"); // mismatched user
        dbRecord.put(Constants.SUB_CATEGORY, subCategory.name());
        dbRecord.put(Constants.CREATED_AT, Instant.now());
        dbRecord.put(Constants.MESSAGE, "{\"data\":{\"discussionId\":\"d1\"}}");

        // ✅ Explicit cast to String avoids ambiguity
        when(objectMapper.readTree(anyString()))
                .thenAnswer(invocation -> realMapper.readTree((String) invocation.getArgument(0)));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, subCategory, "user123", requestNode);

        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }



    @Test
    void testClubNotification_SkipsWhenClubKeyMismatch() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        String userId = "user123";

        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.readTree("{\"message\":{\"data\":{\"discussionId\":\"x\"}}}");

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.USER_ID, userId);
        dbRecord.put(Constants.SUB_CATEGORY, subCategory.name());
        dbRecord.put(Constants.CREATED_AT, Instant.now());
        dbRecord.put(Constants.MESSAGE, "{\"data\":{\"discussionId\":\"y\"}}");

        // ✅ Make mocked objectMapper delegate to real ObjectMapper
        when(objectMapper.readTree(anyString()))
                .thenAnswer(invocation -> realMapper.readTree((String) invocation.getArgument(0)));

        when(cassandraOperation.getRecordsByProperties(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(dbRecord));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);
        method.invoke(notificationService, subCategory, userId, requestNode);

        // ✅ Since keys mismatch, no update should happen
        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }


    @Test
    void testGetNotificationsByUserIdAndLastXDays_FiltersByReadStatusRead() {
        String authToken = "Bearer abc";
        String userId = "u123";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n1");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, true);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.READ, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertEquals(1, notifications.size());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_FiltersByReadStatusUnread() {
        String authToken = "Bearer abc";
        String userId = "u123";
        Instant now = Instant.now();

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n1");
        notif.put(Constants.CREATED_AT, now);
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, false);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.UNREAD, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertEquals(1, notifications.size());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_MergesGlobalAndUserNotifications() {
        String authToken = "Bearer abc";
        String userId = "u123";
        Instant now = Instant.now();

        Map<String, Object> userNotif = new HashMap<>();
        userNotif.put(Constants.NOTIFICATION_ID, "n1");
        userNotif.put(Constants.CREATED_AT, now);
        userNotif.put(Constants.IS_DELETED, false);

        Map<String, Object> globalNotif = new HashMap<>();
        globalNotif.put(Constants.NOTIFICATION_ID, "g1");
        globalNotif.put(Constants.CREATED_AT, now);
        globalNotif.put(Constants.IS_DELETED, false);

        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(userNotif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(globalNotif));

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<?> notifications = (List<?>) result.get(Constants.NOTIFICATIONS);
        assertEquals(2, notifications.size());
    }


    @Test
    void testCreateNotification_BlankUserId() {
        ObjectMapper realMapper = new ObjectMapper(); // real mapper
        JsonNode request = realMapper.createObjectNode().put(Constants.TYPE, "IN_APP");

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("");

        ApiResponse response = notificationService.createNotification(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }


    @Test
    void testCreateNotification_BlankNotificationType() {
        ObjectMapper realMapper = new ObjectMapper(); // real mapper for test JSON
        JsonNode request = realMapper.createObjectNode()
                .set(Constants.REQUEST, realMapper.createObjectNode()); // no type inside

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("u1");

        ApiResponse response = notificationService.createNotification(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }


    @Test
    void testCreateNotification_ExceptionDuringInsert() {
        ObjectMapper realMapper = new ObjectMapper(); // real mapper for test JSON
        JsonNode innerRequest = realMapper.createObjectNode().put(Constants.TYPE, "IN_APP");
        JsonNode request = realMapper.createObjectNode().set(Constants.REQUEST, innerRequest);

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("u1");
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("DB fail"));

        ApiResponse response = notificationService.createNotification(request, "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }


    @Test
    void testBulkCreateNotifications_InvalidUserIds() {
        ObjectMapper realMapper = new ObjectMapper(); // real instance for JSON building

        JsonNode request = realMapper.createObjectNode()
                .set(Constants.REQUEST, realMapper.createObjectNode()
                        .put(Constants.TYPE, "IN_APP")
                        .put(Constants.SUB_CATEGORY, "CONTENT_PUBLISHED")); // no user_ids

        ApiResponse response = notificationService.bulkCreateNotifications(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }


    @Test
    void testBulkCreateNotifications_TooManyUsers() {
        ObjectMapper realMapper = new ObjectMapper(); // real mapper for test JSON

        ArrayNode userIds = realMapper.createArrayNode();
        for (int i = 0; i < Constants.MAX_USER_LIMIT + 1; i++) {
            userIds.add(realMapper.createObjectNode().put(Constants.USER_ID, "u" + i));
        }

        JsonNode request = realMapper.createObjectNode()
                .set(Constants.REQUEST, realMapper.createObjectNode()
                        .put(Constants.TYPE, "IN_APP")
                        .put(Constants.SUB_CATEGORY, "CONTENT_PUBLISHED")
                        .set(Constants.USER_IDS, userIds));

        ApiResponse response = notificationService.bulkCreateNotifications(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }


    @Test
    void testIsGlobalSubCategory_PositiveAndNegative() throws Exception {
        assertTrue(invokeIsGlobalSubCategory(NotificationSubCategory.EVENT_PUBLISHED));
        assertFalse(invokeIsGlobalSubCategory(NotificationSubCategory.LIKED_POST));
    }

    private boolean invokeIsGlobalSubCategory(NotificationSubCategory subCategory) throws Exception {
        Method method = NotificationServiceImpl.class.getDeclaredMethod("isGlobalSubCategory", NotificationSubCategory.class);
        method.setAccessible(true);
        return (boolean) method.invoke(notificationService, subCategory);
    }

    @Test
    void testCreateGlobalNotification_FailedInsert() {
        ObjectMapper realMapper = new ObjectMapper(); // real mapper for building JSON
        JsonNode request = realMapper.createObjectNode().put(Constants.TYPE, "IN_APP");

        ApiResponse failedResponse = new ApiResponse();
        failedResponse.put(Constants.RESPONSE, Constants.FAILED); // mimic failure

        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap()))
                .thenReturn(failedResponse);

        ApiResponse response = notificationService.createGlobalNotification(
                NotificationSubCategory.EVENT_PUBLISHED, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }



    @Test
    void testGetNotificationsByUserIdAndLastXDays_InvalidUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("t")).thenReturn("");

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays("t", 7, 0, 5, NotificationReadStatus.BOTH, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetNotificationsByUserIdAndLastXDays_InvalidSubTypeOrderIndex() {
        String authToken = "Bearer token";
        String userId = "u1";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);

        Map<String, Object> notif = new HashMap<>();
        notif.put(Constants.NOTIFICATION_ID, "n1");
        notif.put(Constants.CREATED_AT, Instant.now());
        notif.put(Constants.IS_DELETED, false);
        notif.put(Constants.READ, false);
        notif.put(Constants.SUB_TYPE, "invalidType"); // triggers Integer.MAX_VALUE

        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_USER_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif));
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of());

        ApiResponse response = notificationService.getNotificationsByUserIdAndLastXDays(
                authToken, 7, 0, 10, NotificationReadStatus.BOTH, null);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testMarkNotificationsAsRead_GlobalAllFlow() {
        String authToken = "t";
        String userId = "u1";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()))
                .thenReturn(List.of(Map.of(Constants.NOTIFICATION_ID, "g1", Constants.CREATED_AT, Instant.now())));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.TYPE, Constants.ALL);
        request.put(Constants.ACTION, Constants.GLOBAL);

        ApiResponse response = notificationService.markNotificationsAsRead(authToken, request);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testMarkNotificationsAsDeleted_FailureUpdate() {
        String authToken = "t";
        String userId = "u1";
        when(accessTokenValidator.fetchUserIdFromAccessToken(authToken)).thenReturn(userId);
        when(cassandraOperation.updateRecord(any(), any(), any(), any()))
                .thenReturn(Map.of(Constants.RESPONSE, Constants.FAILED));

        ApiResponse response = notificationService.markNotificationsAsDeleted(authToken, List.of("n1"));

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(((List<?>) result.get(Constants.NOTIFICATIONS)).isEmpty());
    }


    @Test
    void testGetResetNotificationCount_UserIdBlank() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("t")).thenReturn("");

        ApiResponse response = notificationService.getResetNotificationCount("t");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetInstant_InvalidString() {
        Instant instant = notificationService.getInstant("not-a-date");
        assertNull(instant);
    }

    @Test
    void testPrepareNotificationResponse_MessageParseFails() {
        Map<String, Object> record = new HashMap<>();
        record.put("message", "{invalidJson"); // invalid JSON

        Map<String, Object> result = notificationService.prepareNotificationResponse(record);

        assertTrue(result.containsKey("message"));
    }

    @Test
    void testCreateNotification_DisabledSetting() {
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode request = realMapper.createObjectNode()
                .set(Constants.REQUEST, realMapper.createObjectNode()
                        .put(Constants.TYPE, "IN_APP"));

        NotificationSettingEntity entity = new NotificationSettingEntity();
        entity.setEnabled(false);
        when(accessTokenValidator.fetchUserIdFromAccessToken("t")).thenReturn("u1");
        when(notificationSettingRepository.findByUserIdAndNotificationTypeAndIsDeletedFalse("u1", "IN_APP"))
                .thenReturn(Optional.of(entity));

        ApiResponse response = notificationService.createNotification(request, "t");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(((Map<?, ?>) result).isEmpty());
    }


    @Test
    void testGetUnreadNotificationCount_NoRecords() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("t")).thenReturn("u1");
        when(cassandraOperation.getRecordsByProperties(any(), eq(Constants.TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), eq(1)))
                .thenReturn(Collections.emptyList());

        ApiResponse response = notificationService.getUnreadNotificationCount("t", 7);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals(0, result.get("unread"));
    }

    @Test
    void testCreateGlobalNotification_Exception() {
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode request = realMapper.createObjectNode().put(Constants.TYPE, "IN_APP");

        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("DB error"));

        ApiResponse response = notificationService.createGlobalNotification(NotificationSubCategory.EVENT_PUBLISHED, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testClubNotification_ReadTreeThrowsException() throws Exception {
        NotificationSubCategory subCategory = NotificationSubCategory.LIKED_POST;
        String userId = "user1";
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode requestNode = realMapper.createObjectNode().put("message", "invalidJson");

        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("parse fail"));

        Method method = NotificationServiceImpl.class.getDeclaredMethod(
                "clubNotification", NotificationSubCategory.class, String.class, JsonNode.class);
        method.setAccessible(true);

        // Should not throw
        method.invoke(notificationService, subCategory, userId, requestNode);

        verify(cassandraOperation, never()).updateRecord(any(), any(), any(), any());
    }


    void testMarkNotificationsAsRead_GlobalAll() {
        String userId = "user-123";
        Map<String, Object> request = new HashMap<>();
        request.put("action", "GLOBAL");
        request.put("type", "ALL");

        // Mock methods
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(userId);
        when(cassandraOperation.getRecordsByProperties(
                eq(KEYSPACE_SUNBIRD), eq(TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()
        )).thenReturn(new ArrayList<>()); // Simulate fetching global notifications

        // Call the method
        ApiResponse response = notificationService.markNotificationsAsRead(AUTH_TOKEN, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Global notifications marked as read"));
        assertNotNull(response.getResult());
    }


    @Test
    void testMarkNotificationsAsRead_InvalidType() {
        String userId = "user-123";
        Map<String, Object> request = new HashMap<>();
        request.put("action", "GLOBAL");
        request.put("type", "INVALID_TYPE");

        // Mock methods
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(userId);

        // Call the method
        ApiResponse response = notificationService.markNotificationsAsRead(AUTH_TOKEN, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Invalid type. Allowed values: all, individual"));
    }

    @Test
    void testMarkNotificationsAsRead_MissingType() {
        String userId = "user-123";
        Map<String, Object> request = new HashMap<>();
        request.put("action", "GLOBAL");

        // Mock methods
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(userId);

        // Call the method
        ApiResponse response = notificationService.markNotificationsAsRead(AUTH_TOKEN, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Request type must be provided"));
    }

    @Test
    void testMarkNotificationsAsRead_GlobalIndividual_MatchingNotifications_UsingReflection() throws Exception {
        String userId = "user-123";
        Map<String, Object> request = new HashMap<>();
        request.put("action", "GLOBAL");
        request.put("type", "INDIVIDUAL");
        request.put("ids", Arrays.asList("notification-id-1", "notification-id-2"));

        // Mock methods
        when(accessTokenValidator.fetchUserIdFromAccessToken(AUTH_TOKEN)).thenReturn(userId);

        // Simulate fetching global notifications
        List<Map<String, Object>> globalNotifications = Arrays.asList(
                Map.of(NOTIFICATION_ID, "notification-id-1", "read", false),
                Map.of(NOTIFICATION_ID, "notification-id-2", "read", false)
        );
        when(cassandraOperation.getRecordsByProperties(
                eq(KEYSPACE_SUNBIRD), eq(TABLE_GLOBAL_NOTIFICATION), anyMap(), any(), anyInt()
        )).thenReturn(globalNotifications);

        // Simulate the behavior of insertAndMarkGlobalNotificationsAsRead using reflection
        List<Map<String, Object>> targetNotifications = Arrays.asList(
                Map.of("notificationId", "notification-id-1", "read", true),
                Map.of("notificationId", "notification-id-2", "read", true)
        );

        // Use reflection to access the private method extractIndividualNotificationIds
        Method extractMethod = NotificationServiceImpl.class.getDeclaredMethod("extractIndividualNotificationIds", Map.class, ApiResponse.class);
        extractMethod.setAccessible(true); // Make the private method accessible

        // Create a mock ApiResponse
        ApiResponse mockResponse = new ApiResponse();

        // Invoke the private method using reflection to extract individual notification IDs
        List<String> notificationIds = (List<String>) extractMethod.invoke(notificationService, request, mockResponse);

        // Assert the result from extractIndividualNotificationIds
        assertNotNull(notificationIds);
        assertEquals(2, notificationIds.size());
        assertTrue(notificationIds.contains("notification-id-1"));
        assertTrue(notificationIds.contains("notification-id-2"));

        // Use reflection to access the private method insertAndMarkGlobalNotificationsAsRead
        Method insertMethod = NotificationServiceImpl.class.getDeclaredMethod("insertAndMarkGlobalNotificationsAsRead", String.class, List.class);
        insertMethod.setAccessible(true); // Make the private method accessible

        // Invoke the private method using reflection to insert and mark notifications as read
        List<Map<String, Object>> result = (List<Map<String, Object>>) insertMethod.invoke(
                notificationService, userId, globalNotifications
        );

        // Assert the results from the private method insertAndMarkGlobalNotificationsAsRead
        assertNotNull(result);
        assertEquals(globalNotifications.size(), result.size());

        // Verify that the notifications were marked as read
        for (Map<String, Object> notification : result) {
            assertTrue((Boolean) notification.get("read"));
        }

        // Now, simulate the public method markNotificationsAsRead (this invokes the private methods internally)
        ApiResponse response = notificationService.markNotificationsAsRead(AUTH_TOKEN, request);

        // Assert the response
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("Selected global notifications marked as read and inserted"));
        assertNotNull(response.getResult());
        Map<String, Object> finalResult = (Map<String, Object>) response.getResult();
        assertEquals(targetNotifications.size(), ((List<?>) finalResult.get("notifications")).size());
    }
}
