package com.igot.cb.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.notification.service.NotificationService;
import com.igot.cb.producer.Producer;
import com.igot.cb.util.CbServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.igot.cb.util.Constants.*;

/**
 * Kafka consumer that processes bulk notification create events and delegates
 * directly to {@link NotificationService#bulkCreatePeerValidationNotifications}.
 */
@Service
@Slf4j
public class BulkNotificationCreateConsumer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Producer producer;
    private final CbServerProperties cbServerProperties;

    public BulkNotificationCreateConsumer(NotificationService notificationService,
                                          ObjectMapper objectMapper,
                                          Producer producer,
                                          CbServerProperties cbServerProperties) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.cbServerProperties = cbServerProperties;
    }

    @KafkaListener(
            topics = "${kafka.topic.notification.bulk.create}",
            groupId = "${kafka.group.notification.bulk.create}"
    )
    public void consumeBulkCreate(String message) {
        try {
            if (StringUtils.isNotBlank(message)) {
                CompletableFuture.runAsync(() -> processBulkCreateAsync(message));
            }
        } catch (Exception e) {
            log.error("Unexpected error dispatching bulk notification create message: {}", e.getMessage(), e);
            pushToErrorTopic(message, e);
        }
    }

    private void processBulkCreateAsync(String message) {
        try {
            Map<String, Object> payload = parsePayload(message);
            if (!hasRequestField(payload, message)) {
                return;
            }
            notificationService.bulkCreatePeerValidationNotifications(payload);
            log.info("Bulk notification create completed for notificationId count in payload");
        } catch (Exception e) {
            log.error("Failed to process bulk notification create message: {}", e.getMessage(), e);
            pushToErrorTopic(message, e);
        }
    }

    private Map<String, Object> parsePayload(String message) {
        try {
            return objectMapper.readValue(message, MAP_TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON in bulk notification create message", e);
        }
    }

    private boolean hasRequestField(Map<String, Object> payload, String message) {
        if (!payload.containsKey(REQUEST)) {
            log.warn("Missing required '{}' field in bulk notification create message: {}", REQUEST, message);
            return false;
        }
        return true;
    }

    private void pushToErrorTopic(String message, Exception e) {
        producer.push(cbServerProperties.getKafkaTopicNotificationBulkCreateError(),
                Map.of("originalMessage", message, "errorMessage", e.getMessage()));
    }
}
