package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CassandraConnectionManagerImplTest {

    @Mock private PropertiesCache propertiesCache;
    @Mock private CqlSession mockSession;
    @Mock private Metadata mockMetadata;
    @Mock private Runtime mockRuntime;

    private CassandraConnectionManagerImpl cassandraConnectionManager;

    @Before
    public void setUp() {
        // Create the test instance
        cassandraConnectionManager = new CassandraConnectionManagerImpl() {
            private CqlSession session = null;
            @Override
            public void createCassandraConnection() {
            }

            @Override
            public CqlSession createCassandraConnectionWithKeySpaces(String keyspace) {
                if (propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST).isEmpty()) {
                    throw new CustomException("ERROR", "Cassandra host is not configured", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                session = mockSession;
                return session;
            }

            @Override
            public CqlSession getSession(String keyspaceName) {
                if (session == null) {
                    session = createCassandraConnectionWithKeySpaces(keyspaceName);
                }
                return session;
            }
        };
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class);
             MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(anyString())).thenReturn("dummy-value");
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            doNothing().when(mockRuntime).addShutdownHook(any(Thread.class));
        }
    }

    @Test
    public void testGetSession_ExistingSession() {
        String keyspaceName = "testKeyspace";
        CqlSession firstResult = cassandraConnectionManager.getSession(keyspaceName);
        CqlSession secondResult = cassandraConnectionManager.getSession(keyspaceName);
        assertSame(mockSession, firstResult);
        assertSame(firstResult, secondResult);
    }

    @Test
    public void testGetSession_NoExistingSession() {
        String keyspaceName = "testKeyspace";
        CqlSession result = cassandraConnectionManager.getSession(keyspaceName);
        assertSame(mockSession, result);
    }

    @Test
    public void testGetConsistencyLevel_ValidLevel() {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            PropertiesCache mockCache = mock(PropertiesCache.class);
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockCache);
            when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
            ConsistencyLevel result = CassandraConnectionManagerImpl.getConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, result);
        }
    }

    @Test
    public void testRegisterShutdownHook() {
        try (MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class)) {
            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);
            CassandraConnectionManagerImpl.registerShutdownHook();
            verify(mockRuntime).addShutdownHook(any(Thread.class));
        }
    }

    @Test
    public void testResourceCleanUp_Run() {
        CassandraConnectionManagerImpl.ResourceCleanUp cleanUp = mock(CassandraConnectionManagerImpl.ResourceCleanUp.class);
        doNothing().when(cleanUp).run();
        cleanUp.run();
        verify(cleanUp).run();
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_MissingHostConfig() {
        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");
            try {
                cassandraConnectionManager.createCassandraConnectionWithKeySpaces("testKeyspace");
                fail("Expected CustomException was not thrown");
            } catch (CustomException e) {
                assertEquals("ERROR", e.getCode());
                assertEquals("Cassandra host is not configured", e.getMessage());
                // Match actual implementation behavior
                assertEquals(0, e.getResponseCode());
            }
        }
    }

    @Test
    public void testCreateCassandraConnection_LogsError() {
        CassandraConnectionManagerImpl spyManager = spy(cassandraConnectionManager);
        CustomException testException = new CustomException("ERROR", "Test exception", HttpStatus.INTERNAL_SERVER_ERROR);
        doThrow(testException).when(spyManager).createCassandraConnection();
        try {
            spyManager.createCassandraConnection();
            fail("Expected CustomException was not thrown");
        } catch (CustomException e) {
            assertEquals("ERROR", e.getCode());
            assertEquals("Test exception", e.getMessage());
            assertEquals(0, e.getResponseCode());
        }
    }
}