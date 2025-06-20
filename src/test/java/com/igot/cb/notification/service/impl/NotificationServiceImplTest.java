package com.igot.cb.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.userNotificationSetting.repository.NotificationSettingRepository;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;


import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

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
    private static final String userId = "user-123";

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
    void testCreateNotification_Success_WithComplexMessage() throws Exception {
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
    void testCreateNotification_MissingUserId() throws Exception {
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
        // Sample input where user_ids is an array of objects: [{ "user_id": "user1" }, { "user_id": "user2" }]
        String payload = "{ \"request\": { " +
                "\"type\": \"comment\"," +
                "\"category\": \"content\"," +
                "\"user_ids\": [ {\"user_id\": \"user1\"}, {\"user_id\": \"user2\"} ]" +
                "} }";
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode userNotificationDetail = realMapper.readTree(payload);

        // Mock insertBulkRecord to return a successful ApiResponse
        when(cassandraOperation.insertBulkRecord(
                anyString(), anyString(), anyList()))
                .thenReturn(new ApiResponse(Map.of(Constants.RESPONSE, Constants.SUCCESS).toString()));

        // Spy to pass through prepareNotificationResponse
        NotificationServiceImpl spyService = Mockito.spy(notificationService);
        doAnswer(invocation -> invocation.getArgument(0)).when(spyService).prepareNotificationResponse(any());

        ApiResponse response = spyService.bulkCreateNotifications(userNotificationDetail);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("notifications"));
        List<Map<String, Object>> notifications = (List<Map<String, Object>>) result.get("notifications");
        assertEquals(2, notifications.size());
        Set<String> userIds = new HashSet<>();
        for (Map<String, Object> notif : notifications) {
            userIds.add((String) notif.get(Constants.USER_ID));
        }
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
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), any(), anyInt()))
                .thenReturn(List.of(notif1, notif2));

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
        ApiResponse expectedResponse = ProjectUtil.createDefaultResponse(Constants.USER_NOTIFICATION_READ_NOTIFICATIONID);
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

        when(cassandraOperation.updateRecordByCompositeKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_USER_NOTIFICATION),
                anyMap(),
                anyMap()
        )).thenReturn(updateResponse);

        // Mock fetchNotifications
        Method fetchMethod = NotificationServiceImpl.class.getDeclaredMethod("fetchNotifications", String.class);
        fetchMethod.setAccessible(true);
        ReflectionTestUtils.setField(notificationService, "cassandraOperation", cassandraOperation);

        doReturn(List.of(notification)).when(cassandraOperation).getRecordsByPropertiesWithoutFiltering(
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

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                any(), any(), anyMap(), any(), anyInt()
        )).thenReturn(notifications);

        when(cassandraOperation.updateRecordByCompositeKey(
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
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
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

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
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

        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.TABLE_USER_NOTIFICATION),
                        anyMap(),
                        isNull(),
                        anyInt()))
                .thenReturn(notifications);

        Mockito.when(cassandraOperation.updateRecordByCompositeKey(
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

        Mockito.when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        eq(Constants.KEYSPACE_SUNBIRD),
                        eq(Constants.TABLE_USER_NOTIFICATION),
                        anyMap(),
                        isNull(),
                        anyInt()))
                .thenReturn(notifications);

        Mockito.when(cassandraOperation.updateRecordByCompositeKey(
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
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
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

        when(cassandraOperation.updateRecordByCompositeKey(
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




}
