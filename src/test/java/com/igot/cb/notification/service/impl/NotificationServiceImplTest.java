package com.igot.cb.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.notification.enums.NotificationReadStatus;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;


import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ObjectMapper objectMapper;

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
    void testBulkCreateNotifications_success() throws Exception {
        // Prepare input JSON with two user_ids
        String payload = "{ \"request\": { " +
                "\"type\": \"comment\"," +
                "\"category\": \"content\"," +
                "\"source\": \"userCreated\"," +
                "\"role\": \"user\"," +
                "\"message\": { \"text\": \"Bulk notification\" }," +
                "\"user_ids\": [\"user1\", \"user2\"]" +
                "} }";

        ObjectMapper realMapper = new ObjectMapper();
        JsonNode userNotificationDetail = realMapper.readTree(payload);

        // Mock cassandraOperation.insertBulkRecord to return success
        when(cassandraOperation.insertBulkRecord(
                anyString(), anyString(), anyList()))
                .thenReturn(Map.of("response", "SUCCESS"));

        // Mock prepareNotificationResponse to just return the input map (for simplicity)
        NotificationServiceImpl spyService = Mockito.spy(notificationService);
        doAnswer(invocation -> invocation.getArgument(0)).when(spyService).prepareNotificationResponse(any());

        ApiResponse response = spyService.bulkCreateNotifications(userNotificationDetail);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("notifications"));
        List<Map<String, Object>> notifications = (List<Map<String, Object>>) result.get("notifications");
        assertEquals(2, notifications.size());
        assertEquals("user1", notifications.get(0).get(Constants.USER_ID));
        assertEquals("user2", notifications.get(1).get(Constants.USER_ID));
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
        notif1.put(Constants.NOTIFICATION_ID, "n1");
        notif1.put(Constants.USER_ID, userId);
        notif1.put(Constants.CREATED_AT, now.minusSeconds(3600));
        notif1.put(Constants.READ, false);
        notif1.put(Constants.CATEGORY, "catA");

        Map<String, Object> notif2 = new HashMap<>();
        notif2.put(Constants.NOTIFICATION_ID, "n2");
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
        List<Map<String, Object>> returnedNotifs = (List<Map<String, Object>>) result.get(Constants.NOTIFICATIONS);
        assertEquals(1, returnedNotifs.size());
        assertEquals("n1", returnedNotifs.get(0).get(Constants.NOTIFICATION_ID));
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


}

