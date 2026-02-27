package com.igot.cb.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.notification.service.NotificationService;
import com.igot.cb.util.Constants;

import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class Consumer {

    private NotificationService notificationService;

    private ObjectMapper objectMapper;

    public Consumer(NotificationService notificationService) {
        this.notificationService = notificationService;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "${kafka.topic.name}", groupId = "${kafka.consumer.group-id}")

    public void consume(String message) {
        log.info("Received message from Kafka: {}", message);

        try {
            JsonNode jsonNode = objectMapper.readTree(message);

            if (!jsonNode.has(Constants.REQUEST)) {
                log.warn("Missing 'request' field in message: {}", message);
                return;
            }

            notificationService.bulkCreateNotifications(jsonNode);

            log.info("Notification successfully forwarded to service");

        } catch (Exception e) {
            log.error("Failed to process Kafka message", e);
        }
    }
}
