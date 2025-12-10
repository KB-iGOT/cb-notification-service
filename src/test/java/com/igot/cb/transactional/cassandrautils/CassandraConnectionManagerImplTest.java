package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;

import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class CassandraConnectionManagerImplTest {

    @Mock
    PropertiesCache propertiesCache;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @org.junit.jupiter.api.Test
    void testGetConsistencyLevel_valid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("LOCAL_QUORUM");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
        }
    }

    @org.junit.jupiter.api.Test
    void testGetConsistencyLevel_invalid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("INVALID");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals("LOCAL_QUORUM",level.name());
        }
    }

    @org.junit.jupiter.api.Test
    void testShutdownHook() throws  InterruptedException {
        Thread thread = new CassandraConnectionManagerImpl.ResourceCleanUp();
        thread.start();
        thread.join(1000);
        assertFalse(thread.isAlive() , " Shutdown hook thread should have completed execution");
    }

    private ConsistencyLevel invokeGetConsistencyLevel() {
        try {
            Method method = CassandraConnectionManagerImpl.class.getDeclaredMethod("getConsistencyLevel");
            method.setAccessible(true);
            return (ConsistencyLevel) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConstructorThrowsException_whenHostIsBlank() {
        try (
                MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class)
        ) {
            // Arrange
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            // Act & Assert
            CustomException exception = assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
            assertEquals("Cassandra host is not configured", exception.getMessage()); // Adjust message if needed
        }
    }

    @Test
    void testGetSession_reuseExisting() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        when(mockSession.isClosed()).thenReturn(false);
        getCassandraSessionMap().put("ks1", mockSession);
        CassandraConnectionManagerImpl manager = mock(
                CassandraConnectionManagerImpl.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS) // let real methods run
        );
        CqlSession returned = manager.getSession("ks1");
        assertSame(mockSession, returned);
    }


    @Test
    void testGetSession_createsNewSession_mocked() throws Exception {
        getCassandraSessionMap().clear();
        CassandraConnectionManagerImpl manager =
                mock(CassandraConnectionManagerImpl.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        CqlSession mockSession = mock(CqlSession.class);
        doReturn(mockSession).when(manager).createCassandraConnectionWithKeySpaces("ks2");
        CqlSession returned = manager.getSession("ks2");
        assertSame(mockSession, returned);
        assertTrue(getCassandraSessionMap().containsKey("ks2"));
    }



    @Test
    void testCreateCassandraConnectionWithKeySpaces_exception() {
        try (
            MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class)
        ) {
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            CassandraConnectionManagerImpl manager = mock(
                CassandraConnectionManagerImpl.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS)
            );
            assertThrows(CustomException.class,
                () -> manager.createCassandraConnectionWithKeySpaces(null));
        }
    }


    @Test
    void testCreateCassandraConnection_success_mocked() {
        CassandraConnectionManagerImpl manager = mock(CassandraConnectionManagerImpl.class,
                withSettings().withoutAnnotations().defaultAnswer(CALLS_REAL_METHODS));
        CqlSession mockSession = mock(CqlSession.class);
        doReturn(mockSession).when(manager).createCassandraConnectionWithKeySpaces(any());
        CqlSession session = manager.createCassandraConnectionWithKeySpaces("ks1");
        assertNotNull(session);
        assertSame(mockSession, session);
    }

    @Test
    void testResourceCleanUp_closesSessions() throws Exception {
        CqlSession mockSession1 = mock(CqlSession.class);
        CqlSession mockSession2 = mock(CqlSession.class);

        getCassandraSessionMap().put("ks1", mockSession1);
        setStaticSession(mockSession2);

        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = new CassandraConnectionManagerImpl.ResourceCleanUp();
        cleanup.run();

        verify(mockSession1).close();
        verify(mockSession2).close();
    }

    @Test
    void testGetSession_existingButClosed_createsNew() throws Exception {
        CqlSession closedSession = mock(CqlSession.class);
        when(closedSession.isClosed()).thenReturn(true);
        getCassandraSessionMap().put("ks1", closedSession);

        CassandraConnectionManagerImpl manager =
                mock(CassandraConnectionManagerImpl.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        CqlSession newSession = mock(CqlSession.class);
        doReturn(newSession).when(manager).createCassandraConnectionWithKeySpaces("ks1");

        CqlSession result = manager.getSession("ks1");

        assertSame(newSession, result);
        assertTrue(getCassandraSessionMap().containsKey("ks1"));
        assertEquals(newSession, getCassandraSessionMap().get("ks1"));
    }

    @Test
    void testResourceCleanUp_noSessionsDoesNotThrow() {
        // Clear map and static session
        try {
            getCassandraSessionMap().clear();
            setStaticSession(null);
        } catch (Exception e) {
            fail(e);
        }

        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = new CassandraConnectionManagerImpl.ResourceCleanUp();
        assertDoesNotThrow(cleanup::run);
    }

    @SuppressWarnings("unchecked")
    private Map<String, CqlSession> getCassandraSessionMap() throws Exception {
        Field field = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        field.setAccessible(true);
        return (Map<String, CqlSession>) field.get(null);
    }

    private void setStaticSession(CqlSession session) throws Exception {
        Field field = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        field.setAccessible(true);
        field.set(null, session);
    }

    @Test
    void testGetConsistencyLevel_propertyMissing_defaultsToLocalQuorum() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn(null);

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
        }
    }

}
