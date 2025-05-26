package com.igot.cb.transactional.redis.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "cacheTtl", 3600L);
    }

    @Test
    void putCache_shouldStoreObjectInRedis() throws Exception {
        String key = "testKey";
        TestObject testObject = new TestObject("test value");
        String serializedObject = "{\"value\":\"test value\"}";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(testObject)).thenReturn(serializedObject);
        cacheService.putCache(key, testObject);
        verify(objectMapper).writeValueAsString(testObject);
        verify(redisTemplate).opsForValue();
        verify(valueOperations).set(key, serializedObject, 3600L, TimeUnit.SECONDS);
    }

    @Test
    void putCache_shouldHandleException() throws Exception {
        String key = "testKey";
        TestObject testObject = new TestObject("test value");
        when(objectMapper.writeValueAsString(testObject)).thenThrow(new RuntimeException("Serialization error"));
        cacheService.putCache(key, testObject);
        verify(objectMapper).writeValueAsString(testObject);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void getCache_shouldReturnDataFromRedis() {
        String key = "testKey";
        String cachedValue = "{\"value\":\"test value\"}";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(cachedValue);
        String result = cacheService.getCache(key);
        assertEquals(cachedValue, result);
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(key);
    }

    @Test
    void getCache_shouldReturnNullWhenExceptionOccurs() {
        String key = "testKey";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenThrow(new RuntimeException("Redis error"));
        String result = cacheService.getCache(key);
        assertNull(result);
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(key);
    }

    static class TestObject {
        private String value;

        public TestObject(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Test
    void deleteCache_shouldDeleteCacheWhenKeyExists() {
        String key = "testKey";
        when(redisTemplate.delete(key)).thenReturn(true);
        cacheService.deleteCache(key);
        verify(redisTemplate).delete(key);
    }

    @Test
    void deleteCache_shouldHandleNonExistentKey() {
        String key = "nonExistentKey";
        when(redisTemplate.delete(key)).thenReturn(false);
        cacheService.deleteCache(key);
        verify(redisTemplate).delete(key);
    }
}