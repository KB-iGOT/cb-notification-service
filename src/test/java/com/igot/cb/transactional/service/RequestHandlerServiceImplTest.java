package com.igot.cb.transactional.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
public class RequestHandlerServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RequestHandlerServiceImpl requestHandlerServiceImpl;  // Class to be tested

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this); // Initialize mocks
    }

    @Test
    public void testFetchResultUsingPost_successfulResponse() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);
        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);
        assertNotNull(response);
        assertEquals(expectedResponse, response);
    }

    @Test
    public void testFetchResultUsingPost_httpClientErrorException() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();
        Map<String, String> headersValues = new HashMap<>();
        String errorJson = "{\"error\":\"Unauthorized\"}";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                errorJson.getBytes(),
                null
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);
        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);
        verify(restTemplate).postForObject(eq(uri), any(HttpEntity.class), eq(Map.class));
    }

    protected Map<String, Object> handleHttpClientError(HttpClientErrorException hce) {
        try {
            return new ObjectMapper().readValue(hce.getResponseBodyAsString(),
                    new TypeReference<HashMap<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    public void testFetchResultUsingPost_jsonProcessingException() throws Exception {
        String uri = "http://example.com/api";
        Object request = new Object();  // Your actual request object
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);
        when(restTemplate.postForObject(eq(uri), eq(entity), eq(Map.class))).thenReturn(expectedResponse);
        when(objectMapper.writeValueAsString(request)).thenThrow(JsonProcessingException.class);
        Map<String, Object> response = requestHandlerServiceImpl.fetchResultUsingPost(uri, request, headersValues);
        assertNull(response);  // Expect null as the response will not be properly serialized due to the exception
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_successfulResponse() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        headersValues.put("Authorization", "Bearer token");
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), argThat(entity -> {
            HttpHeaders headers = entity.getHeaders();
            return headers.get("Authorization").contains("Bearer token");
        }), eq(Map.class));
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_httpClientErrorException() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        String errorJson = "{\"error\":\"Not Found\"}";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                errorJson.getBytes(),
                null
        );
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);
        assertNotNull(response);
        Map<String, Object> responseMap = (Map<String, Object>) response;
        assertEquals("Not Found", responseMap.get("error"));
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_generalException() {
        String uri = "http://example.com/api";
        Map<String, String> headersValues = new HashMap<>();
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection error"));
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, headersValues);
        assertNull(response);
        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    public void testFetchUsingGetWithHeadersProfile_nullHeaders() {
        String uri = "http://example.com/api";
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);
        Object response = requestHandlerServiceImpl.fetchUsingGetWithHeadersProfile(uri, null);
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET), argThat(entity ->
                entity.getHeaders() != null && entity.getBody() == null
        ), eq(Map.class));
    }
}
