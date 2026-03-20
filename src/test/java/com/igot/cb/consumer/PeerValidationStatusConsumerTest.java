package com.igot.cb.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.notification.service.NotificationService;
import com.igot.cb.producer.Producer;
import com.igot.cb.util.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeerValidationStatusConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Producer producer;

    @Mock
    private CbServerProperties cbServerProperties;

    private PeerValidationStatusConsumer consumer;

    private static final String ERROR_TOPIC = "dev.peer.validation.status.update.error";

    private static final String VALID_MESSAGE =
            "{\"userId\":\"user1\",\"notificationId\":\"notif1\",\"createdAt\":\"2024-01-01T00:00:00Z\",\"subCategory\":\"PEER_EVALUATION_ASSIGNED\"}";

    @BeforeEach
    void setUp() {
        consumer = new PeerValidationStatusConsumer(notificationService, new ObjectMapper(), producer, cbServerProperties);
    }

    @Test
    void consumeStatusUpdate_blankMessage_doesNotProcess() {
        consumer.consumeStatusUpdate("   ");
        verify(notificationService, after(500).never()).updatePeerValidationStatusToSubmitted(  any(), any(), any(), any());
        verify(producer, after(500).never()).push(anyString(), any());
    }

    @Test
    void consumeStatusUpdate_nullMessage_doesNotProcess() {
        consumer.consumeStatusUpdate(null);
        verify(notificationService, after(500).never()).updatePeerValidationStatusToSubmitted(any(), any(), any(), any());
        verify(producer, after(500).never()).push(anyString(), any());
    }

    @Test
    void consumeStatusUpdate_validMessage_callsService() {
        consumer.consumeStatusUpdate(VALID_MESSAGE);
        verify(notificationService, timeout(2000).times(1))
                .updatePeerValidationStatusToSubmitted("user1", "notif1", "2024-01-01T00:00:00Z", "PEER_EVALUATION_ASSIGNED");
    }

    @Test
    void consumeStatusUpdate_invalidJson_pushesToErrorTopic() {
        when(cbServerProperties.getKafkaTopicPeerValidationError()).thenReturn(ERROR_TOPIC);
        consumer.consumeStatusUpdate("{ not valid json }");
        verify(producer, timeout(2000).times(1)).push(eq(ERROR_TOPIC), any());
        verify(notificationService, never()).updatePeerValidationStatusToSubmitted(any(), any(), any(), any());
    }

    @Test
    void consumeStatusUpdate_missingRequiredField_doesNotCallService() {
        String message = "{\"userId\":\"user1\",\"createdAt\":\"2024-01-01T00:00:00Z\",\"subCategory\":\"PEER_EVALUATION_ASSIGNED\"}";
        consumer.consumeStatusUpdate(message);
        verify(notificationService, after(500).never()).updatePeerValidationStatusToSubmitted(any(), any(), any(), any());
        verify(producer, after(500).never()).push(anyString(), any());
    }

    @Test
    void consumeStatusUpdate_blankFieldValue_doesNotCallService() {
        String message = "{\"userId\":\"\",\"notificationId\":\"notif1\",\"createdAt\":\"2024-01-01T00:00:00Z\",\"subCategory\":\"PEER_EVALUATION_ASSIGNED\"}";
        consumer.consumeStatusUpdate(message);
        verify(notificationService, after(500).never()).updatePeerValidationStatusToSubmitted(any(), any(), any(), any());
        verify(producer, after(500).never()).push(anyString(), any());
    }

    @Test
    void consumeStatusUpdate_serviceThrowsException_pushesToErrorTopic() {
        when(cbServerProperties.getKafkaTopicPeerValidationError()).thenReturn(ERROR_TOPIC);
        doThrow(new RuntimeException("DB error"))
                .when(notificationService)
                .updatePeerValidationStatusToSubmitted(anyString(), anyString(), anyString(), anyString());
        consumer.consumeStatusUpdate(VALID_MESSAGE);
        verify(producer, timeout(2000).times(1)).push(eq(ERROR_TOPIC), any());
    }
}
