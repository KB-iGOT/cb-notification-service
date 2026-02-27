package com.igot.cb.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsumerConfigurationTest {

    private ConsumerConfiguration config;

    @BeforeEach
    void setUp() {
        config = new ConsumerConfiguration();

        // Inject test values manually
        setField(config, "kafkabootstrapAddress", "localhost:9092");
        setField(config, "kafkaOffsetResetValue", "earliest");
        setField(config, "kafkaMaxPollInterval", 300000);
        setField(config, "kafkaMaxPollRecords", 500);
        setField(config, "kafkaAutoCommitInterval", 1000);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConsumerFactory() {
        ConsumerFactory<String, String> factory = config.consumerFactory();
        assertNotNull(factory);
    }

    @Test
    void testKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = (ConcurrentKafkaListenerContainerFactory<String, String>) config.kafkaListenerContainerFactory();
        assertNotNull(factory);
        assertEquals(3000, factory.getContainerProperties().getPollTimeout());
    }
}