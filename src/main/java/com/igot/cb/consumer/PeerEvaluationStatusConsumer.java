package com.igot.cb.consumer;

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
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.igot.cb.util.Constants.*;

/**
 * Kafka consumer that processes peer evaluation results (APPROVED / REJECTED) and updates
 * {@code peer_validation_reviews} and {@code user_notification} tables accordingly.
 * Records already in a terminal status are skipped without error.
 */
@Service
@Slf4j
public class PeerEvaluationStatusConsumer {

    private static final Set<String> REQUIRED_FIELDS = Set.of(
            USER_ID_FIELD, NOTIFICATION_ID_FIELD, CREATED_AT_FIELD, STATUS_FIELD, SUB_CATEGORY_FIELD
    );

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Producer producer;
    private final CbServerProperties cbServerProperties;

    public PeerEvaluationStatusConsumer(NotificationService notificationService,
                                        ObjectMapper objectMapper,
                                        Producer producer,
                                        CbServerProperties cbServerProperties) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.cbServerProperties = cbServerProperties;
    }

    @KafkaListener(
            topics = "${kafka.topic.process.peer.evaluation}",
            groupId = "${kafka.group.process.peer.evaluation}"
    )
    public void consumeEvaluationUpdate(String message) {
        try {
            if (StringUtils.isNotBlank(message)) {
                CompletableFuture.runAsync(() -> processEvaluationUpdateAsync(message));
            }
        } catch (Exception e) {
            log.error("Unexpected error dispatching peer evaluation message: {}", e.getMessage(), e);
            pushToErrorTopic(message, e);
        }
    }

    private void processEvaluationUpdateAsync(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, MAP_TYPE_REF);
            if (!hasAllRequiredFields(payload, message)) {
                return;
            }
            EvaluationUpdateRequest request = buildRequest(payload);
            if (!hasNoBlankFields(request, message)
                    || !hasValidEvaluationStatus(request.getStatus(), message)
                    || !hasValidSubCategory(request.getSubCategory(), message)) {
                return;
            }
            applyEvaluationStatusUpdate(request);
        } catch (Exception e) {
            log.error("Failed to process peer evaluation status update: {}", e.getMessage(), e);
            pushToErrorTopic(message, e);
        }
    }

    private boolean hasAllRequiredFields(Map<String, Object> payload, String message) {
        if (!payload.keySet().containsAll(REQUIRED_FIELDS)) {
            log.warn("Missing required fields in peer evaluation message: {}", message);
            return false;
        }
        return true;
    }

    private EvaluationUpdateRequest buildRequest(Map<String, Object> payload) {
        return EvaluationUpdateRequest.builder()
                .userId((String) payload.get(USER_ID_FIELD))
                .notificationId((String) payload.get(NOTIFICATION_ID_FIELD))
                .createdAt((String) payload.get(CREATED_AT_FIELD))
                .status((String) payload.get(STATUS_FIELD))
                .subCategory((String) payload.get(SUB_CATEGORY_FIELD))
                .build();
    }

    private boolean hasNoBlankFields(EvaluationUpdateRequest request, String message) {
        if (StringUtils.isAnyBlank(request.getUserId(), request.getNotificationId(),
                request.getCreatedAt(), request.getStatus(), request.getSubCategory())) {
            log.warn("Blank field value in peer evaluation message: {}", message);
            return false;
        }
        return true;
    }

    private boolean hasValidEvaluationStatus(String status, String message) {
        if (!STATUS_APPROVED.equalsIgnoreCase(status) && !STATUS_REJECTED.equalsIgnoreCase(status)) {
            log.warn("Invalid evaluation status '{}' in message: {}", status, message);
            return false;
        }
        return true;
    }

    private boolean hasValidSubCategory(String subCategory, String message) {
        if (!SUB_CATEGORY_PEER_REVIEW_ASSIGNED.equalsIgnoreCase(subCategory)) {
            log.warn("Invalid subCategory '{}' in message: {}", subCategory, message);
            return false;
        }
        return true;
    }

    private void applyEvaluationStatusUpdate(EvaluationUpdateRequest request) {
        notificationService.updatePeerEvaluationStatus(
                request.getUserId(),
                request.getNotificationId(),
                request.getCreatedAt(),
                request.getStatus()
        );
        log.info("Peer evaluation status updated for notificationId: {}", request.getNotificationId());
    }

    private void pushToErrorTopic(String message, Exception e) {
        producer.push(cbServerProperties.getKafkaTopicPeerEvaluationError(),
                Map.of("originalMessage", message, "errorMessage", e.getMessage()));
    }

    @Builder
    @Getter
    private static class EvaluationUpdateRequest {
        private final String userId;
        private final String notificationId;
        private final String createdAt;
        private final String status;
        private final String subCategory;
    }
}
