package com.igot.cb.transactional.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestHandlerServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RequestHandlerServiceImpl requestHandlerService;

    @BeforeEach
    void setUp() throws Exception {
        // Create a mock logger
        Logger mockLogger = mock(Logger.class);

        // Use reflection to set the logger
        Field logField = RequestHandlerServiceImpl.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(requestHandlerService, mockLogger);

        // Set up common logger behavior
        when(mockLogger.isDebugEnabled()).thenReturn(true);
    }

    @Test
    void fetchResultUsingPost_Success() {
        // Arrange
        String uri = "http://test.com/api";
        Object request = Map.of("key", "value");
        Map<String, String> headers = Map.of("header1", "value1");
        Map<String, Object> expectedResponse = Map.of("response", "success");

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = requestHandlerService.fetchResultUsingPost(uri, request, headers);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(restTemplate).postForObject(eq(uri), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void fetchResultUsingPost_WithNullHeaders() {
        // Arrange
        String uri = "http://test.com/api";
        Object request = Map.of("key", "value");
        Map<String, Object> expectedResponse = Map.of("response", "success");

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = requestHandlerService.fetchResultUsingPost(uri, request, null);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(restTemplate).postForObject(eq(uri), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void fetchResultUsingPost_HttpClientErrorException() {
        // Arrange
        String uri = "http://test.com/api";
        Object request = Map.of("key", "value");
        Map<String, String> headers = Map.of("header1", "value1");
        String errorResponse = "{\"error\": \"Bad Request\"}";

        when(restTemplate.postForObject(eq(uri), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request",
                        errorResponse.getBytes(), StandardCharsets.UTF_8));

        // Act
        Map<String, Object> result = requestHandlerService.fetchResultUsingPost(uri, request, headers);

        // Assert
        assertNotNull(result);
        assertEquals("Bad Request", result.get("error"));
    }

    @Test
    void fetchResultUsingPost_JsonProcessingException() {
        // Arrange
        String uri = "http://test.com/api";
        Object request = new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("Test exception");
            }
        };
        Map<String, String> headers = Map.of("header1", "value1");

        // Act
        Map<String, Object> result = requestHandlerService.fetchResultUsingPost(uri, request, headers);

        // Assert
        assertNull(result);
    }

    @Test
    void fetchUsingGetWithHeadersProfile_Success() {
        // Arrange
        String uri = "http://test.com/api";
        Map<String, String> headers = Map.of("header1", "value1");
        Map<String, Object> expectedResponse = Map.of("response", "success");
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        // Act
        Object result = requestHandlerService.fetchUsingGetWithHeadersProfile(uri, headers);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void fetchUsingGetWithHeadersProfile_HttpClientErrorException() {
        // Arrange
        String uri = "http://test.com/api";
        Map<String, String> headers = Map.of("header1", "value1");
        String errorResponse = "{\"error\": \"Bad Request\"}";

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request",
                        errorResponse.getBytes(), StandardCharsets.UTF_8));

        // Act
        Object result = requestHandlerService.fetchUsingGetWithHeadersProfile(uri, headers);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("Bad Request", resultMap.get("error"));
    }

    @Test
    void fetchUsingGetWithHeadersProfile_GeneralException() {
        // Arrange
        String uri = "http://test.com/api";
        Map<String, String> headers = Map.of("header1", "value1");

        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // Act
        Object result = requestHandlerService.fetchUsingGetWithHeadersProfile(uri, headers);

        // Assert
        assertNull(result);
    }
}