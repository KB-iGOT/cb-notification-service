package com.igot.cb.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

class ConsumerTest {

    @InjectMocks
    private Consumer consumer;

    @Mock
    private NotificationService notificationService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void testConsume_messageMissingRequest_doesNotCallService() throws Exception {
        // Message with no "request" field
        String message = "{ \"foo\": \"bar\" }";
        Consumer spyConsumer = Mockito.spy(consumer);
        ReflectionTestUtils.setField(spyConsumer, "objectMapper", objectMapper);

        spyConsumer.consume(message);

        verify(notificationService, never()).bulkCreateNotifications(any());
    }

    @Test
    void testConsume_invalidJson_logsError() {
        String invalidMessage = "{ invalid json ";
        Consumer spyConsumer = Mockito.spy(consumer);
        ReflectionTestUtils.setField(spyConsumer, "objectMapper", objectMapper);

        spyConsumer.consume(invalidMessage);

        verify(notificationService, never()).bulkCreateNotifications(any());
    }

    @Test
    void testConsume_serviceThrowsException_logsError() throws Exception {
        String message = "{ \"request\": { \"user_ids\": [{\"user_id\": \"user1\"}] } }";

        Consumer spyConsumer = Mockito.spy(consumer);
        ReflectionTestUtils.setField(spyConsumer, "objectMapper", objectMapper);

        doThrow(new RuntimeException("test exception"))
                .when(notificationService).bulkCreateNotifications(any(JsonNode.class));

        spyConsumer.consume(message);

        verify(notificationService, times(1)).bulkCreateNotifications(any(JsonNode.class));
    }
}