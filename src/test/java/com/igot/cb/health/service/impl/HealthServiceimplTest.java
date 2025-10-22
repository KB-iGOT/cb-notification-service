package com.igot.cb.health.service.impl;

import com.igot.cb.health.service.HealthServiceImpl;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthServiceImplTest {

    @InjectMocks
    private HealthServiceImpl healthService;

    @Mock
    private CassandraOperation cassandraOperation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCheckHealthStatus_Healthy() throws Exception {
        List<Map<String, Object>> cassandraResponse = List.of(Map.of("dummyKey", "dummyValue"));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), isNull(), isNull(), anyInt()
        )).thenReturn(cassandraResponse);

        ApiResponse statusResp = healthService.checkHealthStatus();

        assertEquals(HttpStatus.OK, statusResp.getResponseCode());
        assertTrue((Boolean) statusResp.get(Constants.HEALTHY));

        List<Map<String, Object>> checks = (List<Map<String, Object>>) statusResp.get(Constants.CHECKS);
        assertNotNull(checks);
        assertFalse(checks.isEmpty());
        Map<String, Object> cassandraCheck = checks.get(0);
        assertEquals(Constants.CASSANDRA_DB, cassandraCheck.get(Constants.NAME));
        assertTrue((Boolean) cassandraCheck.get(Constants.HEALTHY));
    }

    @Test
    void testCheckHealthStatus_Unhealthy() throws Exception {
        // Mock Cassandra returns an empty list
        List<Map<String, Object>> cassandraResponse = Collections.emptyList();
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), isNull(), isNull(), anyInt()
        )).thenReturn(cassandraResponse);

        ApiResponse statusResp = healthService.checkHealthStatus();

        assertEquals(HttpStatus.OK, statusResp.getResponseCode());
        assertFalse((Boolean) statusResp.get(Constants.HEALTHY));

        List<Map<String, Object>> checks = (List<Map<String, Object>>) statusResp.get(Constants.CHECKS);
        assertNotNull(checks);
        assertFalse(checks.isEmpty());
        Map<String, Object> cassandraCheck = checks.get(0);
        assertEquals(Constants.CASSANDRA_DB, cassandraCheck.get(Constants.NAME));
        assertFalse((Boolean) cassandraCheck.get(Constants.HEALTHY));
    }

    @Test
    void testCheckHealthStatus_Exception() throws Exception {
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), isNull(), isNull(), anyInt()
        )).thenThrow(new RuntimeException("Cassandra error"));

        ApiResponse statusResp = healthService.checkHealthStatus();

        assertEquals(HttpStatus.OK, statusResp.getResponseCode());
        assertFalse((Boolean) statusResp.get(Constants.HEALTHY));

        List<Map<String, Object>> checks = (List<Map<String, Object>>) statusResp.get(Constants.CHECKS);
        assertNotNull(checks);
        assertFalse(checks.isEmpty());
        Map<String, Object> cassandraCheck = checks.get(0);
        assertEquals(Constants.CASSANDRA_DB, cassandraCheck.get(Constants.NAME));
        assertFalse((Boolean) cassandraCheck.get(Constants.HEALTHY));
    }

    @Test
    void testCheckHealthStatus_whenCassandraHealthCheckThrows_setsInternalServerError() throws Exception {
        // Spy to partially mock the service
        HealthServiceImpl spyService = Mockito.spy(healthService);

        // Force cassandraHealthStatus to throw exception
        doThrow(new RuntimeException("Simulated failure")).when(spyService).cassandraHealthStatus(any(ApiResponse.class));

        // Invoke the method
        ApiResponse statusResp = spyService.checkHealthStatus();

        // Assert response is marked as failed
        assertEquals(Constants.FAILED, statusResp.getParams().getStatus());
        assertEquals("Simulated failure", statusResp.getParams().getErr());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, statusResp.getResponseCode());
    }

}
