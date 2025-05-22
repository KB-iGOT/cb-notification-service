package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.springframework.data.cassandra.core.CassandraAdminTemplate;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class SunbirdConfigTest {

    private SunbirdConfig sunbirdConfig;
    private CqlSession mockSession;
    private CqlSessionBuilder mockBuilder;
    private CassandraConverter mockConverter;

    @Before
    public void setup() {
        sunbirdConfig = new SunbirdConfig() {
            @Override
            protected String getLocalDataCenter() {
                return "datacenter1";
            }

            @Override
            public CassandraConverter cassandraConverter() {
                return new MappingCassandraConverter(new CassandraMappingContext());
            }
        };
        mockSession = mock(CqlSession.class);
        mockBuilder = mock(CqlSessionBuilder.class);
        mockConverter = mock(CassandraConverter.class);
        ReflectionTestUtils.setField(sunbirdConfig, "contactPoints", "localhost");
        ReflectionTestUtils.setField(sunbirdConfig, "port", 9042);
        ReflectionTestUtils.setField(sunbirdConfig, "keyspaceName", "test_keyspace");
        ReflectionTestUtils.setField(sunbirdConfig, "sunbirdUser", "");
        ReflectionTestUtils.setField(sunbirdConfig, "sunbirdPassword", "");
        when(mockBuilder.addContactPoint(any(InetSocketAddress.class))).thenReturn(mockBuilder);
        when(mockBuilder.withLocalDatacenter(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withKeyspace(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withAuthCredentials(anyString(), anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockSession);
    }

    @Test
    public void testCassandraTemplate() {
        CassandraAdminTemplate template = sunbirdConfig.cassandraTemplate(mockSession);
        assertNotNull("Template should not be null", template);
    }

    @Test
    public void testCqlSessionWithoutCredentials() {
        // Set up static mocking for CqlSession
        try (MockedStatic<CqlSession> cqlSessionMock = mockStatic(CqlSession.class)) {
            cqlSessionMock.when(CqlSession::builder).thenReturn(mockBuilder);
            CqlSession session = sunbirdConfig.cqlSession();
            assertNotNull("Session should not be null", session);
            verify(mockBuilder).addContactPoint(any(InetSocketAddress.class));
            verify(mockBuilder).withLocalDatacenter(eq("datacenter1"));
            verify(mockBuilder).withKeyspace(eq("test_keyspace"));
            verify(mockBuilder, never()).withAuthCredentials(anyString(), anyString());
            verify(mockBuilder).build();
        }
    }

    @Test
    public void testCqlSessionWithCredentials() {
        ReflectionTestUtils.setField(sunbirdConfig, "sunbirdUser", "username");
        ReflectionTestUtils.setField(sunbirdConfig, "sunbirdPassword", "password");
        try (MockedStatic<CqlSession> cqlSessionMock = mockStatic(CqlSession.class)) {
            cqlSessionMock.when(CqlSession::builder).thenReturn(mockBuilder);
            CqlSession session = sunbirdConfig.cqlSession();
            assertNotNull("Session should not be null", session);
            verify(mockBuilder).withAuthCredentials(eq("username"), eq("password"));
        }
    }

    @Test
    public void testCqlSessionWithEmptyPassword() {
        ReflectionTestUtils.setField(sunbirdConfig, "sunbirdUser", "username");
        ReflectionTestUtils.setField(sunbirdConfig, "sunbirdPassword", "");
        try (MockedStatic<CqlSession> cqlSessionMock = mockStatic(CqlSession.class)) {
            cqlSessionMock.when(CqlSession::builder).thenReturn(mockBuilder);
            CqlSession session = sunbirdConfig.cqlSession();
            assertNotNull("Session should not be null", session);
            verify(mockBuilder, never()).withAuthCredentials(anyString(), anyString());
        }
    }

    @Test
    public void testGetterAndSetterMethods() {
        ReflectionTestUtils.setField(sunbirdConfig, "contactPoints", "cassandra1,cassandra2");
        assertEquals("cassandra1,cassandra2", sunbirdConfig.getContactPoints());
        ReflectionTestUtils.setField(sunbirdConfig, "port", 9043);
        assertEquals(9043, sunbirdConfig.getPort());
        ReflectionTestUtils.setField(sunbirdConfig, "keyspaceName", "another_keyspace");
        assertEquals("another_keyspace", sunbirdConfig.getKeyspaceName());
    }

    @Test
    public void testLocalDataCenterUsage() {
        SunbirdConfig customConfig = new SunbirdConfig() {
            @Override
            protected String getLocalDataCenter() {
                return "test-datacenter";
            }
        };
        ReflectionTestUtils.setField(customConfig, "contactPoints", "localhost");
        ReflectionTestUtils.setField(customConfig, "port", 9042);
        ReflectionTestUtils.setField(customConfig, "keyspaceName", "test_keyspace");
        ReflectionTestUtils.setField(customConfig, "sunbirdUser", "");  // Critical - was missing
        ReflectionTestUtils.setField(customConfig, "sunbirdPassword", "");  // Critical - was missing
        try (MockedStatic<CqlSession> cqlSessionMock = mockStatic(CqlSession.class)) {
            cqlSessionMock.when(CqlSession::builder).thenReturn(mockBuilder);
            customConfig.cqlSession();
            verify(mockBuilder).withLocalDatacenter(eq("test-datacenter"));
        }
    }
}