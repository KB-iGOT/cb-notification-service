package com.igot.cb.transactional.redis.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import com.igot.cb.util.CbServerProperties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import java.time.Duration;
import static org.mockito.Mockito.mock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private CbServerProperties cbServerProperties;

    @InjectMocks
    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisConfig, "redisHost", "localhost");
        ReflectionTestUtils.setField(redisConfig, "redisPort", 6379);
    }

    @Test
    void redisConnectionFactory_shouldReturnLettuceConnectionFactory() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof LettuceConnectionFactory);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        assertEquals("localhost", lettuceFactory.getHostName());
        assertEquals(6379, lettuceFactory.getPort());
        assertEquals(0, lettuceFactory.getDatabase());
    }
    
    @Test
    void buildPoolConfig_shouldConfigurePoolWithProperties() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        assertNotNull(factory);
        verify(cbServerProperties).getRedisPoolMaxTotal();
        verify(cbServerProperties).getRedisPoolMaxIdle();
        verify(cbServerProperties).getRedisPoolMinIdle();
        verify(cbServerProperties).getRedisPoolMaxWait();
    }

    @Test
    void buildPoolConfig_setsCorrectPoolPropertiesFromCbServerProperties() {
        when(cbServerProperties.getRedisPoolMaxTotal()).thenReturn(8);
        when(cbServerProperties.getRedisPoolMaxIdle()).thenReturn(8);
        when(cbServerProperties.getRedisPoolMinIdle()).thenReturn(0);
        when(cbServerProperties.getRedisPoolMaxWait()).thenReturn(1000);
        RedisConfig spyRedisConfig = spy(redisConfig);
        GenericObjectPoolConfig<?> poolConfig = ReflectionTestUtils.invokeMethod(
                spyRedisConfig, "buildPoolConfig");
        assertNotNull(poolConfig);
        assertEquals(8, poolConfig.getMaxTotal());
        assertEquals(8, poolConfig.getMaxIdle());
        assertEquals(0, poolConfig.getMinIdle());
        assertEquals(Duration.ofMillis(1000), poolConfig.getMaxWaitDuration());
        verify(cbServerProperties).getRedisPoolMaxTotal();
        verify(cbServerProperties).getRedisPoolMaxIdle();
        verify(cbServerProperties).getRedisPoolMinIdle();
        verify(cbServerProperties).getRedisPoolMaxWait();
    }

    @Test
    void redisTemplate_shouldReturnProperlyConfiguredRedisTemplate() {
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
        RedisTemplate<String, String> template = redisConfig.redisTemplate(mockFactory);
        assertNotNull(template);
        assertEquals(mockFactory, template.getConnectionFactory());
        assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getValueSerializer() instanceof StringRedisSerializer);
        assertTrue(template.getHashKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getHashValueSerializer() instanceof StringRedisSerializer);
    }
}