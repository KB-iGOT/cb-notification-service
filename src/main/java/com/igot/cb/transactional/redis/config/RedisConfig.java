package com.igot.cb.transactional.redis.config;

import com.igot.cb.util.CbServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;


import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

  @Value("${spring.redis.host}")
  private String redisHost;

  @Value("${spring.redis.port}")
  private int redisPort;

  @Autowired
  private CbServerProperties cbServerProperties;

  @Bean
  public RedisConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
    configuration.setHostName(redisHost);
    configuration.setPort(redisPort);
    configuration.setDatabase(0);
    LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(cbServerProperties.getRedisConnectionTimeout()))
            .poolConfig(buildPoolConfig())
            .build();
    return new LettuceConnectionFactory(configuration, clientConfig);
  }
  
  private GenericObjectPoolConfig<?> buildPoolConfig() {
    GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
    poolConfig.setMaxTotal(cbServerProperties.getRedisPoolMaxTotal());
    poolConfig.setMaxIdle(cbServerProperties.getRedisPoolMaxIdle());
    poolConfig.setMinIdle(cbServerProperties.getRedisPoolMinIdle());
    poolConfig.setMaxWait(Duration.ofMillis(cbServerProperties.getRedisPoolMaxWait()));
    return poolConfig;
  }

  @Bean
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
    RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(redisConnectionFactory);
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.setValueSerializer(new StringRedisSerializer());
    redisTemplate.setHashKeySerializer(new StringRedisSerializer());
    redisTemplate.setHashValueSerializer(new StringRedisSerializer());
    return redisTemplate;
  }
}
