package com.igot.cb;

import org.apache.hc.client5.http.classic.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class CbNotificationApplicationTest {

    @Test
    void testRestTemplateBeanAndClientHttpRequestFactory() {
        // Given
        CbNotificationApplication app = new CbNotificationApplication();

        // When
        RestTemplate restTemplate = app.restTemplate();

        // Then
        assertNotNull(restTemplate, "RestTemplate should not be null");
        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertTrue(factory instanceof HttpComponentsClientHttpRequestFactory,
                "RequestFactory should be instance of HttpComponentsClientHttpRequestFactory");

        // Optional: Validate timeout setting and connection manager indirectly
        HttpComponentsClientHttpRequestFactory httpFactory = (HttpComponentsClientHttpRequestFactory) factory;
        HttpClient httpClient = httpFactory.getHttpClient();
        assertNotNull(httpClient);
    }
}
