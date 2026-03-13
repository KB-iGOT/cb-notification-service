package com.igot.cb.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.notification.service.NotificationService;
import com.igot.cb.producer.Producer;
import com.igot.cb.util.CbServerProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.igot.cb.util.Constants.*;

/**
 * Kafka consumer for processing peer validation status updates.
 * Listens to status change events and updates corresponding records in the database.
 * <p>
 * <strong>Business Rule:</strong> Only PEER_EVALUATION_ASSIGNED submissions are supported.
 * Other sub-categories will be rejected by the service layer.
 */
@Service
@Slf4j
public class PeerValidationStatusConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Producer producer;
    private final CbServerProperties cbServerProperties;

    public PeerValidationStatusConsumer(NotificationService notificationService,
                                        ObjectMapper objectMapper,
                                        Producer producer,
                                        CbServerProperties cbServerProperties) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.cbServerProperties = cbServerProperties;
    }

    /**
     * Listens to Kafka events for updating peer validation records to SUBMITTED status.
     * <strong>Note:</strong> Only {@code PEER_EVALUATION_ASSIGNED} subCategory is supported
     * for status submissions. Messages with other subCategories will be rejected.
     *
     * @param message JSON string containing status update information
     */
    @KafkaListener(
            topics = "${kafka.topic.process.peer.validation}",
            groupId = "${kafka.group.process.peer.validation}"
    )
    public void consumeStatusUpdate(String message) {
        try {
            if (StringUtils.isNotBlank(message)) {
                CompletableFuture.runAsync(() -> processStatusUpdateAsync(message));
            }
        } catch (Exception e) {
            log.error("Error in PeerValidationStatusConsumer: {}", e.getMessage(), e);
            producer.push(cbServerProperties.getKafkaTopicPeerValidationError(), Map.of("originalMessage", message, "errorMessage", e.getMessage()));
        }
    }

    private void processStatusUpdateAsync(String message) {
        try {
            Map<String, Object> messageMap = parseMessage(message);
            if (!isValidMessage(messageMap, message)) {
                return;
            }
            StatusUpdateRequest request = extractStatusUpdateRequest(messageMap);
            if (!isValidRequest(request, message)) {
                return;
            }
            processStatusUpdate(request);
            log.info("Successfully processed peer validation status update for notificationId: {}",
                    request.getNotificationId());
        } catch (Exception e) {
            log.error("Failed to process peer validation status update from Kafka: {}", e.getMessage(), e);
            producer.push(cbServerProperties.getKafkaTopicPeerValidationError(), Map.of("originalMessage", message, "errorMessage", e.getMessage()));
        }
    }

    /**
     * Parses the incoming Kafka message into a Map.
     *
     * @param message JSON string message
     * @return parsed Map
     * @throws JsonProcessingException if parsing fails
     */
    private Map<String, Object> parseMessage(String message) throws JsonProcessingException {
        return objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * Validates that all required fields are present in the message.
     *
     * @param messageMap      Parsed message map
     * @param originalMessage Original message for logging
     * @return true if valid, false otherwise
     */
    private boolean isValidMessage(Map<String, Object> messageMap, String originalMessage) {
        if (!messageMap.containsKey(USER_ID_FIELD) ||
                !messageMap.containsKey(NOTIFICATION_ID_FIELD) ||
                !messageMap.containsKey(CREATED_AT_FIELD) ||
                !messageMap.containsKey(SUB_CATEGORY_FIELD)) {
            log.warn("Missing required fields in status update message: {}", originalMessage);
            return false;
        }
        return true;
    }

    /**
     * Extracts status update request data from the message map.
     *
     * @param messageMap Parsed message map
     * @return StatusUpdateRequest object
     */
    private StatusUpdateRequest extractStatusUpdateRequest(Map<String, Object> messageMap) {
        return StatusUpdateRequest.builder()
                .userId((String) messageMap.get(USER_ID_FIELD))
                .notificationId((String) messageMap.get(NOTIFICATION_ID_FIELD))
                .createdAt((String) messageMap.get(CREATED_AT_FIELD))
                .subCategory((String) messageMap.get(SUB_CATEGORY_FIELD))
                .build();
    }

    /**
     * Validates that all fields in the request are non-blank.
     *
     * @param request         Status update request
     * @param originalMessage Original message for logging
     * @return true if valid, false otherwise
     */
    private boolean isValidRequest(StatusUpdateRequest request, String originalMessage) {
        if (StringUtils.isBlank(request.getUserId()) ||
                StringUtils.isBlank(request.getNotificationId()) ||
                StringUtils.isBlank(request.getCreatedAt()) ||
                StringUtils.isBlank(request.getSubCategory())) {
            log.warn("One or more required fields are blank in status update message: {}", originalMessage);
            return false;
        }
        return true;
    }

    /**
     * Processes the status update by delegating to the notification service.
     *
     * @param request Status update request containing all necessary data
     */
    private void processStatusUpdate(StatusUpdateRequest request) {
        notificationService.updatePeerValidationStatusToSubmitted(
                request.getUserId(),
                request.getNotificationId(),
                request.getCreatedAt(),
                request.getSubCategory()
        );
    }

    /**
     * Internal DTO for status update requests.
     */
    @Builder
    @Getter
    private static class StatusUpdateRequest {
        private final String userId;
        private final String notificationId;
        private final String createdAt;
        private final String subCategory;
    }
}
