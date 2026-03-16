package com.igot.cb.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.notification.entity.NotificationSettingEntity;
import com.igot.cb.notification.repository.NotificationSettingRepository;
import com.igot.cb.util.CbServerProperties;
import com.igot.cb.util.Constants;

import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.igot.cb.util.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BulkPeerValidationNotificationTest {

    @Spy
    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private CbServerProperties cbServerProperties;

    private final ObjectMapper realMapper = new ObjectMapper();

    private static final String USER_1 = "user-001";
    private static final String USER_2 = "user-002";
    private static final String TYPE_VAL = "peer-review";
    private static final String CATEGORY_VAL = "PEER_VALIDATION";
    private static final String SUB_CATEGORY_VAL = "PEER_EVALUATION_ASSIGNED";
    private static final String SUB_TYPE_VAL = "peer_evaluation";
    private static final String SOURCE_VAL = "competency-passbook";
    private static final String SURVEY_END = Instant.now().plus(30, ChronoUnit.DAYS).toString();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        org.springframework.test.util.ReflectionTestUtils.setField(
                notificationService, "objectMapper", realMapper);
        when(cbServerProperties.isPeerValidationNotificationSettingCheckEnabled()).thenReturn(false);
        when(cbServerProperties.getPeerValidationBulkUserNotificationLimit()).thenReturn(100);
        when(cassandraOperation.insertBulkRecord(anyString(), anyString(), anyList()))
                .thenReturn(new ApiResponse());
        when(cassandraOperation.getRecordsByProperties(
                anyString(), eq(TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), anyInt()))
                .thenReturn(Collections.emptyList());
    }

    private Map<String, Object> buildValidRequest(String userId) {
        Map<String, Object> surveyData = new LinkedHashMap<>();
        surveyData.put("surveyEndDate", SURVEY_END);
        surveyData.put("surveyId", "survey-123");
        surveyData.put("title", "Q1 Assessment");
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("data", List.of(surveyData));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put(USER_ID, userId);
        req.put(TYPE, TYPE_VAL);
        req.put(CATEGORY, CATEGORY_VAL);
        req.put(SUB_CATEGORY, SUB_CATEGORY_VAL);
        req.put(SUB_TYPE, SUB_TYPE_VAL);
        req.put(SOURCE, SOURCE_VAL);
        req.put(MESSAGE, message);
        return req;
    }

    private Map<String, Object> wrapRequestBody(List<Map<String, Object>> requestList) {
        return Map.of(REQUEST, requestList);
    }

    @Nested
    @DisplayName("Request validation")
    class ValidationTests {

        @Test
        @DisplayName("null request list → BAD_REQUEST with invalid payload message")
        void nullRequestList() {
            Map<String, Object> body = new HashMap<>();
            body.put(REQUEST, null);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(body);
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(INVALID_PAYLOAD_ERR_MSG, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("empty request list → BAD_REQUEST")
        void emptyRequestList() {
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(Collections.emptyList()));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(INVALID_PAYLOAD_ERR_MSG, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("exceeds user limit → BAD_REQUEST with limit message")
        void exceedsUserLimit() {
            when(cbServerProperties.getPeerValidationBulkUserNotificationLimit()).thenReturn(2);
            List<Map<String, Object>> list = List.of(
                    buildValidRequest("u1"), buildValidRequest("u2"), buildValidRequest("u3"));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(list));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertTrue(res.getParams().getErrMsg().contains("2"));
        }

        @Test
        @DisplayName("missing user_id → BAD_REQUEST")
        void missingUserId() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.remove(USER_ID);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_USER_ID_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("missing type → BAD_REQUEST")
        void missingType() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.remove(TYPE);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_TYPE_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("missing category → BAD_REQUEST")
        void missingCategory() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.remove(CATEGORY);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_CATEGORY_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("invalid category → BAD_REQUEST")
        void invalidCategory() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.put(CATEGORY, "INVALID_CAT");
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertTrue(res.getParams().getErrMsg().contains("INVALID_CAT"));
        }

        @Test
        @DisplayName("missing sub_category → BAD_REQUEST")
        void missingSubCategory() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.remove(SUB_CATEGORY);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_SUB_CATEGORY_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("invalid sub_category → BAD_REQUEST")
        void invalidSubCategory() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.put(SUB_CATEGORY, "NOT_A_SUB_CATEGORY");
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertTrue(res.getParams().getErrMsg().contains("NOT_A_SUB_CATEGORY"));
        }

        @Test
        @DisplayName("missing sub_type → BAD_REQUEST")
        void missingSubType() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.remove(SUB_TYPE);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_SUB_TYPE_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("missing source → BAD_REQUEST")
        void missingSource() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.remove(SOURCE);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_SOURCE_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("message is a string instead of object → BAD_REQUEST")
        void messageIsString() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.put(MESSAGE, "plain text");
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_MESSAGE_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("message is null → BAD_REQUEST")
        void messageIsNull() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.put(MESSAGE, null);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_MESSAGE_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("message.data is empty list → BAD_REQUEST")
        void messageDataEmpty() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.put(MESSAGE, Map.of("data", Collections.emptyList()));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_MESSAGE_DATA_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("message.data missing → BAD_REQUEST")
        void messageDataMissing() {
            Map<String, Object> req = buildValidRequest(USER_1);
            req.put(MESSAGE, Map.of("other", "value"));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_MESSAGE_DATA_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("missing surveyEndDate in message.data → BAD_REQUEST")
        void missingSurveyEndDate() {
            Map<String, Object> req = buildValidRequest(USER_1);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("data", List.of(Map.of("surveyId", "s1")));
            req.put(MESSAGE, msg);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_SURVEY_END_DATE_REQUIRED, res.getParams().getErrMsg());
        }

        @Test
        @DisplayName("invalid surveyEndDate format → BAD_REQUEST")
        void invalidSurveyEndDateFormat() {
            Map<String, Object> req = buildValidRequest(USER_1);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("data", List.of(Map.of("surveyEndDate", "not-a-date")));
            req.put(MESSAGE, msg);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(req)));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
            assertEquals(ERR_SURVEY_END_DATE_FORMAT, res.getParams().getErrMsg());
        }
    }

    @Nested
    @DisplayName("Successful creation")
    class SuccessTests {

        @Test
        @DisplayName("single user → inserts notification + action + unread count")
        void singleUser_success() {
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            assertEquals(HttpStatus.OK, res.getResponseCode());
            assertEquals(SUCCESS, res.getParams().getStatus());
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            List<?> notifications = (List<?>) result.get(NOTIFICATIONS);
            assertEquals(1, notifications.size());
            assertEquals(1, result.get(PROCESSED_COUNT));
            assertEquals(0, result.get(SKIPPED_COUNT));
            assertEquals(0, result.get(FAILED_COUNT));
            Map<?, ?> notif = (Map<?, ?>) notifications.get(0);
            assertEquals(USER_1, notif.get(USER_ID));
            assertEquals(TYPE_VAL, notif.get(TYPE));
            assertEquals(CATEGORY_VAL, notif.get(CATEGORY));
            assertEquals(SUB_CATEGORY_VAL, notif.get(SUB_CATEGORY));
            assertEquals(SUB_TYPE_VAL, notif.get(SUB_TYPE));
            assertEquals(SOURCE_VAL, notif.get(SOURCE));
            assertNotNull(notif.get(NOTIFICATION_ID));
            assertNotNull(notif.get(CREATED_AT));
            assertFalse(notif.containsKey(IS_DELETED));
            assertFalse(notif.containsKey(UPDATED_AT));
            assertFalse(notif.containsKey(READ_AT));
            assertEquals(STATUS_PENDING, notif.get(STATUS));
            verify(cassandraOperation).insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_USER_NOTIFICATION), anyList());
            verify(cassandraOperation).insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyList());
            verify(cassandraOperation).getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), anyInt());
            verify(cassandraOperation, times(3)).insertBulkRecord(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("multiple users → correct processed count and separate notification IDs")
        void multipleUsers_success() {
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(
                            buildValidRequest(USER_1),
                            buildValidRequest(USER_2))));
            assertEquals(HttpStatus.OK, res.getResponseCode());
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            List<?> notifications = (List<?>) result.get(NOTIFICATIONS);
            assertEquals(2, notifications.size());
            assertEquals(2, result.get(PROCESSED_COUNT));
            String id1 = (String) ((Map<?, ?>) notifications.get(0)).get(NOTIFICATION_ID);
            String id2 = (String) ((Map<?, ?>) notifications.get(1)).get(NOTIFICATION_ID);
            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("created_at is serialized as ISO string in response")
        void createdAt_isFormattedAsString() {
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            List<?> notifications = (List<?>) result.get(NOTIFICATIONS);
            Object createdAt = ((Map<?, ?>) notifications.get(0)).get(Constants.CREATED_AT);
            assertInstanceOf(String.class, createdAt, "created_at should be returned as ISO string");
        }

        @Test
        @DisplayName("message is serialized as JSON string in notification record")
        void message_serializedAsJson() {
            notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
            verify(cassandraOperation).insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_USER_NOTIFICATION), captor.capture());
            List<Map<String, Object>> inserted = captor.getValue();
            assertEquals(1, inserted.size());
            Object messageVal = inserted.get(0).get(MESSAGE);
            assertInstanceOf(String.class, messageVal, "message should be serialized to JSON string for Cassandra");
            assertEquals(STATUS_PENDING, inserted.get(0).get(STATUS));
        }

        @Test
        @DisplayName("action record contains correct survey_end_date and metadata")
        void actionRecord_fields() {
            notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
            verify(cassandraOperation).insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), captor.capture());
            List<Map<String, Object>> actionRecords = captor.getValue();
            assertEquals(1, actionRecords.size());
            Map<String, Object> action = actionRecords.get(0);
            assertEquals(USER_1, action.get(USER_ID));
            assertFalse(action.containsKey(SUB_CATEGORY), "sub_category should be removed before insert");
            assertNotNull(action.get(NOTIFICATION_ID));
            assertNotNull(action.get(Constants.CREATED_AT));
            assertInstanceOf(Instant.class, action.get(SURVEY_END_DATE));
            assertInstanceOf(String.class, action.get(METADATA));
            assertTrue(((String) action.get(METADATA)).contains("survey-123"));
            assertNull(action.get(ACTION_AT), "action_at should be null initially");
            assertEquals(STATUS_PENDING, action.get(STATUS));
        }

        @Test
        @DisplayName("existing unread count is incremented")
        void unreadCount_incremented() {
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), anyInt()))
                    .thenReturn(List.of(Map.of(USER_ID, USER_1, COUNT, 3)));
            notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
            verify(cassandraOperation).insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_UNREAD_NOTIFICATION_COUNT), captor.capture());
            List<Map<String, Object>> upserted = captor.getValue();
            assertEquals(1, upserted.size());
            assertEquals(4, upserted.get(0).get(COUNT));
        }

        @Test
        @DisplayName("no prior unread count → count starts at 1")
        void unreadCount_newUser() {
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_UNREAD_NOTIFICATION_COUNT), anyMap(), anyList(), anyInt()))
                    .thenReturn(Collections.emptyList());
            notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
            verify(cassandraOperation).insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_UNREAD_NOTIFICATION_COUNT), captor.capture());
            assertEquals(1, captor.getValue().get(0).get(COUNT));
        }
    }

    @Nested
    @DisplayName("Notification setting filtering")
    class NotificationSettingTests {

        @Test
        @DisplayName("user with disabled notification type → skipped")
        void disabledNotificationType_isSkipped() {
            when(cbServerProperties.isPeerValidationNotificationSettingCheckEnabled()).thenReturn(true);
            NotificationSettingEntity disabledSetting = new NotificationSettingEntity();
            disabledSetting.setUserId(USER_1);
            disabledSetting.setNotificationType(TYPE_VAL);
            disabledSetting.setEnabled(false);
            when(notificationSettingRepository.findByUserIdInAndNotificationTypeInAndIsDeletedFalse(
                    anyList(), anyList()))
                    .thenReturn(List.of(disabledSetting));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            assertEquals(HttpStatus.OK, res.getResponseCode());
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            assertEquals(0, result.get(PROCESSED_COUNT));
            assertEquals(1, result.get(SKIPPED_COUNT));
            List<?> skipped = (List<?>) result.get(SKIPPED);
            assertEquals(USER_1, ((Map<?, ?>) skipped.get(0)).get(USER_ID));
            assertEquals(NOTIFICATION_TYPE_DISABLED, ((Map<?, ?>) skipped.get(0)).get(REASON));
            verify(cassandraOperation, never()).insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_INDIVIDUAL_NOTIFICATION), anyList());
        }

        @Test
        @DisplayName("user with enabled notification type → not skipped")
        void enabledNotificationType_isProcessed() {
            when(cbServerProperties.isPeerValidationNotificationSettingCheckEnabled()).thenReturn(true);
            NotificationSettingEntity enabledSetting = new NotificationSettingEntity();
            enabledSetting.setUserId(USER_1);
            enabledSetting.setNotificationType(TYPE_VAL);
            enabledSetting.setEnabled(true);
            when(notificationSettingRepository.findByUserIdInAndNotificationTypeInAndIsDeletedFalse(
                    anyList(), anyList()))
                    .thenReturn(List.of(enabledSetting));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            assertEquals(HttpStatus.OK, res.getResponseCode());
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            assertEquals(1, result.get(PROCESSED_COUNT));
            assertEquals(0, result.get(SKIPPED_COUNT));
        }

        @Test
        @DisplayName("setting check disabled → all users processed regardless")
        void settingCheckDisabled_allProcessed() {
            when(cbServerProperties.isPeerValidationNotificationSettingCheckEnabled()).thenReturn(false);
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(
                            buildValidRequest(USER_1),
                            buildValidRequest(USER_2))));
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            assertEquals(2, result.get(PROCESSED_COUNT));
            assertEquals(0, result.get(SKIPPED_COUNT));
            verifyNoInteractions(notificationSettingRepository);
        }

        @Test
        @DisplayName("mix of skipped and eligible → both lists populated")
        void mixedSkippedAndEligible() {
            when(cbServerProperties.isPeerValidationNotificationSettingCheckEnabled()).thenReturn(true);
            NotificationSettingEntity disabledSetting = new NotificationSettingEntity();
            disabledSetting.setUserId(USER_1);
            disabledSetting.setNotificationType(TYPE_VAL);
            disabledSetting.setEnabled(false);
            when(notificationSettingRepository.findByUserIdInAndNotificationTypeInAndIsDeletedFalse(
                    anyList(), anyList()))
                    .thenReturn(List.of(disabledSetting));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(
                            buildValidRequest(USER_1),
                            buildValidRequest(USER_2))));
            assertEquals(HttpStatus.OK, res.getResponseCode());
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            assertEquals(1, result.get(PROCESSED_COUNT));
            assertEquals(1, result.get(SKIPPED_COUNT));
        }

        @Test
        @DisplayName("all users skipped → errMsg indicates no eligible users")
        void allSkipped_errMsg() {
            when(cbServerProperties.isPeerValidationNotificationSettingCheckEnabled()).thenReturn(true);
            NotificationSettingEntity disabledSetting = new NotificationSettingEntity();
            disabledSetting.setUserId(USER_1);
            disabledSetting.setNotificationType(TYPE_VAL);
            disabledSetting.setEnabled(false);
            when(notificationSettingRepository.findByUserIdInAndNotificationTypeInAndIsDeletedFalse(
                    anyList(), anyList()))
                    .thenReturn(List.of(disabledSetting));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            assertEquals(HttpStatus.OK, res.getResponseCode());
            assertEquals(NO_ELIGIBLE_USERS_MSG, res.getParams().getErrMsg());
        }
    }

    @Nested
    @DisplayName("Error and edge cases")
    class ErrorTests {

        @Test
        @DisplayName("cassandra bulk insert throws → INTERNAL_SERVER_ERROR")
        void cassandraInsertThrows_internalError() {
            when(cassandraOperation.insertBulkRecord(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_USER_NOTIFICATION), anyList()))
                    .thenThrow(new RuntimeException("Cassandra timeout"));
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getResponseCode());
        }

        @Test
        @DisplayName("response contains api id = PEER_VALIDATION_BULK_CREATE")
        void responseApiId() {
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    wrapRequestBody(List.of(buildValidRequest(USER_1))));
            assertEquals(PEER_VALIDATION_BULK_CREATE, res.getId());
        }

        @Test
        @DisplayName("requestBody with no 'request' key → BAD_REQUEST")
        void noRequestKey() {
            ApiResponse res = notificationService.bulkCreatePeerValidationNotifications(
                    Map.of("something", "else"));
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
        }
    }

    @Test
    @DisplayName("PEER_REVIEW_ASSIGNED routes to peer_validation_reviews table")
    void peerReviewAssigned_routesToReviewsTable() {
        Map<String, Object> req = buildValidRequest(USER_1);
        req.put(SUB_CATEGORY, "PEER_REVIEW_ASSIGNED");
        notificationService.bulkCreatePeerValidationNotifications(wrapRequestBody(List.of(req)));
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(cassandraOperation).insertBulkRecord(
                eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REVIEWS), captor.capture());
        List<Map<String, Object>> actionRecords = captor.getValue();
        assertEquals(1, actionRecords.size());
        assertEquals(USER_1, actionRecords.get(0).get(USER_ID));
        assertFalse(actionRecords.get(0).containsKey(SUB_CATEGORY));
    }

    @Test
    @DisplayName("mixed sub_categories route to respective tables")
    void mixedSubCategories_routeCorrectly() {
        Map<String, Object> evalReq = buildValidRequest(USER_1);
        evalReq.put(SUB_CATEGORY, "PEER_EVALUATION_ASSIGNED");
        Map<String, Object> reviewReq = buildValidRequest(USER_2);
        reviewReq.put(SUB_CATEGORY, "PEER_REVIEW_ASSIGNED");
        notificationService.bulkCreatePeerValidationNotifications(
                wrapRequestBody(List.of(evalReq, reviewReq)));
        ArgumentCaptor<List<Map<String, Object>>> evalCaptor = ArgumentCaptor.forClass(List.class);
        verify(cassandraOperation).insertBulkRecord(
                eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), evalCaptor.capture());
        assertEquals(1, evalCaptor.getValue().size());
        assertEquals(USER_1, evalCaptor.getValue().get(0).get(USER_ID));
        ArgumentCaptor<List<Map<String, Object>>> reviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(cassandraOperation).insertBulkRecord(
                eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REVIEWS), reviewCaptor.capture());
        assertEquals(1, reviewCaptor.getValue().size());
        assertEquals(USER_2, reviewCaptor.getValue().get(0).get(USER_ID));
    }

    @Nested
    @DisplayName("Get peer validation notifications list")
    class GetPeerValidationListTests {
        private static final String TEST_TOKEN = "list-test-token";
        private static final String TEST_USER_ID = "list-user-abc";
        @BeforeEach
        void setUp() {
            when(accessTokenValidator.fetchUserIdFromAccessToken(TEST_TOKEN)).thenReturn(TEST_USER_ID);
            when(cbServerProperties.getPeerValidationListMaxFetch()).thenReturn(100);
            when(cbServerProperties.getPeerEvaluationAssignedExcludedStatuses())
                    .thenReturn(List.of("SUBMITTED", "IGNORED"));
            when(cbServerProperties.getPeerReviewAssignedExcludedStatuses())
                    .thenReturn(List.of("APPROVED", "REJECTED"));
        }
        private Map<String, Object> buildRecord(String status, Instant createdAt) {
            Map<String, Object> r = new HashMap<>();
            r.put(Constants.STATUS, status);
            r.put(Constants.CREATED_AT, createdAt);
            r.put(Constants.USER_ID, TEST_USER_ID);
            r.put(NOTIFICATION_ID, java.util.UUID.randomUUID().toString());
            return r;
        }
        private Map<String, Object> buildRecordWithSurveyEndDate(String status, Instant createdAt, Instant surveyEndDate) {
            Map<String, Object> r = buildRecord(status, createdAt);
            r.put(Constants.SURVEY_END_DATE, surveyEndDate);
            return r;
        }
        @Test
        @DisplayName("invalid auth token → BAD_REQUEST")
        void invalidAuthToken_returnsBadRequest() {
            when(accessTokenValidator.fetchUserIdFromAccessToken(TEST_TOKEN)).thenReturn("");
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
        }
        @Test
        @DisplayName("unknown subType → BAD_REQUEST")
        void unknownSubType_returnsBadRequest() {
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, "UNKNOWN_TYPE", 7, 0, 10);
            assertEquals(HttpStatus.BAD_REQUEST, res.getResponseCode());
        }
        @Test
        @DisplayName("PEER_EVALUATION_ASSIGNED excludes SUBMITTED and IGNORED statuses")
        void peerEvaluationAssigned_excludesSubmittedAndIgnoredStatuses() {
            Instant now = Instant.now();
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecord("PENDING", now.minusSeconds(10)),
                            buildRecord("SUBMITTED", now.minusSeconds(20)),
                            buildRecord("IGNORED", now.minusSeconds(30))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            assertEquals(HttpStatus.OK, res.getResponseCode());
            List<?> list = (List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS);
            assertEquals(1, list.size());
            assertEquals("PENDING", ((Map<?, ?>) list.get(0)).get(Constants.STATUS));
        }
        @Test
        @DisplayName("PEER_REVIEW_ASSIGNED excludes APPROVED and REJECTED statuses")
        void peerReviewAssigned_excludesApprovedAndRejectedStatuses() {
            Instant now = Instant.now();
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REVIEWS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecord("PENDING", now.minusSeconds(10)),
                            buildRecord("APPROVED", now.minusSeconds(20)),
                            buildRecord("REJECTED", now.minusSeconds(30))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_REVIEW_ASSIGNED, 7, 0, 10);
            assertEquals(HttpStatus.OK, res.getResponseCode());
            List<?> list = (List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS);
            assertEquals(1, list.size());
            assertEquals("PENDING", ((Map<?, ?>) list.get(0)).get(Constants.STATUS));
        }
        @Test
        @DisplayName("SUBMITTED status is excluded — status values from DB are uppercase")
        void submittedStatus_isExcluded() {
            Instant now = Instant.now();
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecord("SUBMITTED", now.minusSeconds(10)),
                            buildRecord("PENDING", now.minusSeconds(20))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            List<?> list = (List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS);
            assertEquals(1, list.size());
            assertEquals("PENDING", ((Map<?, ?>) list.get(0)).get(Constants.STATUS));
        }
        @Test
        @DisplayName("PENDING record with expired survey_end_date is marked EXPIRED in response")
        void pendingRecord_withExpiredSurveyEndDate_isMarkedExpiredInResponse() {
            Instant now = Instant.now();
            when(cbServerProperties.getPeerEvaluationAssignedExcludedStatuses())
                    .thenReturn(Collections.emptyList());
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecordWithSurveyEndDate("PENDING", now.minusSeconds(10),
                                    now.minus(1, ChronoUnit.DAYS))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            List<?> list = (List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS);
            assertEquals(1, list.size());
            assertEquals(Constants.STATUS_EXPIRED, ((Map<?, ?>) list.get(0)).get(Constants.STATUS));
        }
        @Test
        @DisplayName("PENDING record with future survey_end_date remains PENDING")
        void pendingRecord_withFutureSurveyEndDate_remainsPending() {
            Instant now = Instant.now();
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecordWithSurveyEndDate("PENDING", now.minusSeconds(10),
                                    now.plus(7, ChronoUnit.DAYS))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            List<?> list = (List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS);
            assertEquals(1, list.size());
            assertEquals("PENDING", ((Map<?, ?>) list.get(0)).get(Constants.STATUS));
        }
        @Test
        @DisplayName("non-PENDING record with expired survey_end_date status is unchanged")
        void nonPendingRecord_withExpiredSurveyEndDate_statusUnchanged() {
            Instant now = Instant.now();
            when(cbServerProperties.getPeerEvaluationAssignedExcludedStatuses())
                    .thenReturn(Collections.emptyList());
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecordWithSurveyEndDate("SUBMITTED", now.minusSeconds(10),
                                    now.minus(1, ChronoUnit.DAYS))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            List<?> list = (List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS);
            assertEquals(1, list.size());
            assertEquals("SUBMITTED", ((Map<?, ?>) list.get(0)).get(Constants.STATUS));
        }
        @Test
        @DisplayName("EXPIRED status is filtered out when EXPIRED is in exclusion config")
        void expiredStatus_isFiltered_whenInExclusionConfig() {
            Instant now = Instant.now();
            when(cbServerProperties.getPeerEvaluationAssignedExcludedStatuses())
                    .thenReturn(List.of("SUBMITTED", "IGNORED", "EXPIRED"));
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecordWithSurveyEndDate("PENDING", now.minusSeconds(10),
                                    now.minus(1, ChronoUnit.DAYS)),
                            buildRecord("PENDING", now.minusSeconds(20))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            List<?> list = (List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS);
            assertEquals(1, list.size());
            assertEquals("PENDING", ((Map<?, ?>) list.get(0)).get(Constants.STATUS));
        }
        @Test
        @DisplayName("records outside day window are excluded")
        void recordsOutsideDayWindow_areExcluded() {
            Instant now = Instant.now();
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecord("PENDING", now.minusSeconds(60)),
                            buildRecord("PENDING", now.minus(30, ChronoUnit.DAYS))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            assertEquals(1, ((List<?>) ((Map<?, ?>) res.getResult()).get(NOTIFICATIONS)).size());
        }
        @Test
        @DisplayName("pagination returns correct page subset and total count")
        void pagination_returnsCorrectSubsetAndTotalCount() {
            Instant now = Instant.now();
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(List.of(
                            buildRecord("PENDING", now.minusSeconds(10)),
                            buildRecord("PENDING", now.minusSeconds(20)),
                            buildRecord("PENDING", now.minusSeconds(30))));
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 1, 1);
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            assertEquals(3, result.get(Constants.TOTAL_COUNT));
            assertEquals(1, ((List<?>) result.get(NOTIFICATIONS)).size());
        }
        @Test
        @DisplayName("no records returns OK with empty list and zero total")
        void noRecords_returnsOkWithEmptyListAndZeroTotal() {
            when(cassandraOperation.getRecordsByProperties(
                    eq(KEYSPACE_SUNBIRD), eq(TABLE_PEER_VALIDATION_REQUESTS), anyMap(), isNull(), eq(100)))
                    .thenReturn(Collections.emptyList());
            ApiResponse res = notificationService.getPeerValidationNotifications(
                    TEST_TOKEN, SUB_CATEGORY_PEER_EVALUATION_ASSIGNED, 7, 0, 10);
            assertEquals(HttpStatus.OK, res.getResponseCode());
            Map<?, ?> result = (Map<?, ?>) res.getResult();
            assertEquals(0, result.get(Constants.TOTAL_COUNT));
            assertTrue(((List<?>) result.get(NOTIFICATIONS)).isEmpty());
        }
    }

}
