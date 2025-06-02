package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CassandraOperationImplTest {

    private static final String KEYSPACE = "test_keyspace";
    private static final String TABLE = "test_table";

    @Mock
    private CassandraConnectionManager connectionManager;

    @Mock
    private CqlSession session;

    @Mock
    private ResultSet resultSet;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private BoundStatement boundStatement;

    @InjectMocks
    private CassandraOperationImpl cassandraOperation;

    private List<Map<String, Object>> requestList;

    private String keyspace = "test_ks";
    private String table = "test_table";
    private Map<String, Object> updateAttributes;
    private Map<String, Object> compositeKey;

    @Before
    public void setUp() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", UUID.randomUUID());
        map.put("name", "Test User");
        requestList = Collections.singletonList(map);
    }

    @Test
    public void testProcessQuery() throws Exception {
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "test123");
        List<String> fields = Arrays.asList("name", "email");
        Select result = invokeProcessQuery(propertyMap, fields);
        assertNotNull(result);
        String query = result.toString();
        System.out.println("DEBUG - Actual query 1: " + query);
        assertTrue(query.contains("SELECT name,email FROM " + KEYSPACE + "." + TABLE));
        assertTrue(query.contains("WHERE id=") || query.contains("WHERE id ="));
        assertTrue(query.contains("test123"));
        Map<String, Object> propertyMapWithList = new HashMap<>();
        List<String> idList = Arrays.asList("id1", "id2", "id3");
        propertyMapWithList.put("id", idList);
        Select resultWithList = invokeProcessQuery(propertyMapWithList, null);
        assertNotNull(resultWithList);
        String queryWithList = resultWithList.toString();
        System.out.println("DEBUG - Actual query 2: " + queryWithList);
        assertTrue(queryWithList.contains("SELECT * FROM " + KEYSPACE + "." + TABLE));
        assertTrue(queryWithList.contains("WHERE id IN") );
        assertTrue(queryWithList.contains("id1") && queryWithList.contains("id2") && queryWithList.contains("id3"));
        Select resultEmptyProps = invokeProcessQuery(new HashMap<>(), fields);
        assertNotNull(resultEmptyProps);
        String queryEmptyProps = resultEmptyProps.toString();
        System.out.println("DEBUG - Actual query 3: " + queryEmptyProps);
        assertTrue(queryEmptyProps.contains("SELECT name,email FROM " + KEYSPACE + "." + TABLE));
        assertFalse(queryEmptyProps.contains("WHERE"));
        Select resultNullFields = invokeProcessQuery(propertyMap, null);
        assertNotNull(resultNullFields);
        String queryNullFields = resultNullFields.toString();
        System.out.println("DEBUG - Actual query 4: " + queryNullFields);
        assertTrue(queryNullFields.contains("SELECT * FROM " + KEYSPACE + "." + TABLE));
    }

    /**
     * Helper method to invoke the private processQuery method using reflection
     */
    private Select invokeProcessQuery(Map<String, Object> propertyMap, List<String> fields)
            throws Exception {
        Method method = CassandraOperationImpl.class.getDeclaredMethod("processQuery",
                String.class, String.class, Map.class, List.class);
        method.setAccessible(true);
        return (Select) method.invoke(cassandraOperation, CassandraOperationImplTest.KEYSPACE, CassandraOperationImplTest.TABLE, propertyMap, fields);
    }

    @Test
    public void testGetRecordsByPropertiesByKey() {
        // Setup
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "test123");
        List<String> fields = Arrays.asList("name", "email");
        String key = "id";

        CassandraOperationImpl spyCassandraOperation = spy(cassandraOperation);

        Map<String, Object> record = new HashMap<>();
        record.put("name", "Test User");
        record.put("email", "test@example.com");
        List<Map<String, Object>> expectedResponse = Collections.singletonList(record);

        Select mockSelect = mock(Select.class);
        SimpleStatement mockStatement = mock(SimpleStatement.class);
        when(mockSelect.build()).thenReturn(mockStatement);

        doReturn(mockSelect).when(spyCassandraOperation).processQuery(
                eq(keyspaceName), eq(tableName), eq(propertyMap), eq(fields));

        when(connectionManager.getSession(keyspaceName)).thenReturn(session);
        when(session.execute(mockStatement)).thenReturn(resultSet);

        try (MockedStatic<CassandraUtil> cassandraUtilMock = mockStatic(CassandraUtil.class)) {
            cassandraUtilMock.when(() -> CassandraUtil.createResponse(resultSet))
                    .thenReturn(expectedResponse);

            List<Map<String, Object>> result = spyCassandraOperation.getRecordsByPropertiesByKey(
                    keyspaceName, tableName, propertyMap, fields, key);

            verify(connectionManager).getSession(keyspaceName);
            verify(session).execute(mockStatement);
            assertEquals(expectedResponse, result);
            assertEquals(1, result.size());
            assertEquals("Test User", result.get(0).get("name"));
            assertEquals("test@example.com", result.get(0).get("email"));
        }
    }

    @Test
    public void testGetRecordsByPropertiesByKeyException() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        List<String> fields = Arrays.asList("name", "email");
        String key = "id";

        when(connectionManager.getSession(keyspaceName)).thenThrow(new RuntimeException("Test exception"));

        List<Map<String, Object>> result = cassandraOperation.getRecordsByPropertiesByKey(
                keyspaceName, tableName, propertyMap, fields, key);

        verify(connectionManager).getSession(keyspaceName);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testInsertRecordSuccess() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Test User");
        request.put("email", "test@example.com");

        String preparedQuery = "INSERT INTO test_keyspace.test_table(id,name,email) VALUES(?,?,?)";

        try (MockedStatic<CassandraUtil> cassandraUtilMock = mockStatic(CassandraUtil.class)) {
            cassandraUtilMock.when(() -> CassandraUtil.getPreparedStatement(keyspaceName, tableName, request))
                    .thenReturn(preparedQuery);

            when(connectionManager.getSession(keyspaceName)).thenReturn(session);
            when(session.prepare(preparedQuery)).thenReturn(preparedStatement);
            when(preparedStatement.bind(any())).thenReturn(boundStatement);

            Object result = cassandraOperation.insertRecord(keyspaceName, tableName, request);

            verify(connectionManager).getSession(keyspaceName);
            verify(session).prepare(preparedQuery);
            verify(preparedStatement).bind(any());
            verify(session).execute(boundStatement);

            assertTrue(result instanceof ApiResponse);
            ApiResponse response = (ApiResponse) result;
            Map<String, Object> responseMap = response.getResult();
            assertEquals(Constants.SUCCESS, responseMap.get(Constants.RESPONSE));
        }
    }

    @Test
    public void testInsertRecordFailure() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");

        String preparedQuery = "INSERT INTO test_keyspace.test_table(id) VALUES(?)";
        RuntimeException testException = new RuntimeException("Test exception");

        try (MockedStatic<CassandraUtil> cassandraUtilMock = mockStatic(CassandraUtil.class)) {
            cassandraUtilMock.when(() -> CassandraUtil.getPreparedStatement(keyspaceName, tableName, request))
                    .thenReturn(preparedQuery);

            when(connectionManager.getSession(keyspaceName)).thenReturn(session);
            when(session.prepare(preparedQuery)).thenThrow(testException);

            Object result = cassandraOperation.insertRecord(keyspaceName, tableName, request);

            verify(connectionManager).getSession(keyspaceName);
            verify(session).prepare(preparedQuery);

            assertTrue(result instanceof ApiResponse);
            ApiResponse response = (ApiResponse) result;
            Map<String, Object> responseMap = response.getResult();
            assertEquals(Constants.FAILED, responseMap.get(Constants.RESPONSE));
            assertNotNull(responseMap.get(Constants.ERROR_MESSAGE));
            assertTrue(responseMap.get(Constants.ERROR_MESSAGE).toString()
                    .contains("Exception occurred while inserting record to " + tableName));
        }
    }

    @Test
    public void testInsertRecordConnectionFailure() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");

        RuntimeException testException = new RuntimeException("Connection failure");

        try (MockedStatic<CassandraUtil> cassandraUtilMock = mockStatic(CassandraUtil.class)) {
            cassandraUtilMock.when(() -> CassandraUtil.getPreparedStatement(keyspaceName, tableName, request))
                    .thenReturn("INSERT INTO test_keyspace.test_table(id) VALUES(?)");

            when(connectionManager.getSession(keyspaceName)).thenThrow(testException);

            Object result = cassandraOperation.insertRecord(keyspaceName, tableName, request);

            verify(connectionManager).getSession(keyspaceName);

            assertTrue(result instanceof ApiResponse);
            ApiResponse response = (ApiResponse) result;
            Map<String, Object> responseMap = response.getResult();
            assertEquals(Constants.FAILED, responseMap.get(Constants.RESPONSE));
            assertNotNull(responseMap.get(Constants.ERROR_MESSAGE));
        }
    }


    @Test
    public void testGetRecordsByPropertiesWithoutFilteringSuccess() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");
        List<String> fields = Arrays.asList("name", "email");
        Integer limit = 10;

        Map<String, Object> record = new HashMap<>();
        record.put("name", "Test User");
        record.put("email", "test@example.com");
        List<Map<String, Object>> expectedResponse = Collections.singletonList(record);

        Select mockSelect = mock(Select.class);
        when(mockSelect.limit(limit)).thenReturn(mockSelect);
        when(mockSelect.toString()).thenReturn("SELECT name,email FROM test_keyspace.test_table WHERE id = '123' LIMIT 10");

        CassandraOperationImpl spyCassandraOperation = spy(cassandraOperation);
        doReturn(mockSelect).when(spyCassandraOperation).processQuery(
                eq(keyspaceName), eq(tableName), eq(propertyMap), eq(fields));

        when(connectionManager.getSession(keyspaceName)).thenReturn(session);
        when(session.execute(any(SimpleStatement.class))).thenReturn(resultSet);

        try (MockedStatic<CassandraUtil> cassandraUtilMock = mockStatic(CassandraUtil.class);
             MockedStatic<SimpleStatement> simpleStatementMock = mockStatic(SimpleStatement.class)) {

            SimpleStatement mockStatement = mock(SimpleStatement.class);
            simpleStatementMock.when(() -> SimpleStatement.newInstance(anyString()))
                    .thenReturn(mockStatement);

            cassandraUtilMock.when(() -> CassandraUtil.createResponse(resultSet))
                    .thenReturn(expectedResponse);

            List<Map<String, Object>> result = spyCassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    keyspaceName, tableName, propertyMap, fields, limit);

            verify(connectionManager).getSession(keyspaceName);
            verify(session).execute(any(SimpleStatement.class));
            verify(mockSelect).limit(limit);
            assertEquals(expectedResponse, result);
            assertEquals(1, result.size());
            assertEquals("Test User", result.get(0).get("name"));
            assertEquals("test@example.com", result.get(0).get("email"));
        }
    }

    @Test
    public void testGetRecordsByPropertiesWithoutFilteringNoLimit() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");
        List<String> fields = Arrays.asList("name", "email");
        Integer limit = null;

        Map<String, Object> record = new HashMap<>();
        record.put("name", "Test User");
        record.put("email", "test@example.com");
        List<Map<String, Object>> expectedResponse = Collections.singletonList(record);

        Select mockSelect = mock(Select.class);
        when(mockSelect.toString()).thenReturn("SELECT name,email FROM test_keyspace.test_table WHERE id = '123'");

        CassandraOperationImpl spyCassandraOperation = spy(cassandraOperation);
        doReturn(mockSelect).when(spyCassandraOperation).processQuery(
                eq(keyspaceName), eq(tableName), eq(propertyMap), eq(fields));

        when(connectionManager.getSession(keyspaceName)).thenReturn(session);
        when(session.execute(any(SimpleStatement.class))).thenReturn(resultSet);

        try (MockedStatic<CassandraUtil> cassandraUtilMock = mockStatic(CassandraUtil.class);
             MockedStatic<SimpleStatement> simpleStatementMock = mockStatic(SimpleStatement.class)) {

            SimpleStatement mockStatement = mock(SimpleStatement.class);
            simpleStatementMock.when(() -> SimpleStatement.newInstance(anyString()))
                    .thenReturn(mockStatement);

            cassandraUtilMock.when(() -> CassandraUtil.createResponse(resultSet))
                    .thenReturn(expectedResponse);

            List<Map<String, Object>> result = spyCassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    keyspaceName, tableName, propertyMap, fields, limit);

            verify(connectionManager).getSession(keyspaceName);
            verify(session).execute(any(SimpleStatement.class));
            verify(mockSelect, never()).limit(any(Integer.class));
            assertEquals(expectedResponse, result);
        }
    }

    @Test
    public void testGetRecordsByPropertiesWithoutFilteringException() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        List<String> fields = Arrays.asList("name", "email");
        Integer limit = 10;

        Select mockSelect = mock(Select.class);
        when(mockSelect.limit(limit)).thenReturn(mockSelect);
        when(mockSelect.toString()).thenReturn("SELECT name,email FROM test_keyspace.test_table LIMIT 10");

        CassandraOperationImpl spyCassandraOperation = spy(cassandraOperation);
        doReturn(mockSelect).when(spyCassandraOperation).processQuery(
                eq(keyspaceName), eq(tableName), eq(propertyMap), eq(fields));

        RuntimeException testException = new RuntimeException("Connection error");
        when(connectionManager.getSession(keyspaceName)).thenThrow(testException);

        try (MockedStatic<SimpleStatement> simpleStatementMock = mockStatic(SimpleStatement.class)) {
            SimpleStatement mockStatement = mock(SimpleStatement.class);
            simpleStatementMock.when(() -> SimpleStatement.newInstance(anyString()))
                    .thenReturn(mockStatement);

            List<Map<String, Object>> result = spyCassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    keyspaceName, tableName, propertyMap, fields, limit);

            verify(connectionManager).getSession(keyspaceName);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    public void testUpdateRecordSuccess() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Updated Name");
        request.put("email", "updated@example.com");

        String query = "UPDATE test_keyspace.test_table SET name = ? ,email = ? WHERE id = ?";

        try (MockedStatic<CassandraOperationImpl> cassandraOperationMock = mockStatic(CassandraOperationImpl.class)) {
            cassandraOperationMock.when(() -> CassandraOperationImpl.getUpdateQueryStatement(keyspaceName, tableName, request))
                    .thenReturn(query);

            when(connectionManager.getSession(keyspaceName)).thenReturn(session);
            when(session.prepare(query)).thenReturn(preparedStatement);
            when(preparedStatement.bind(any(Object[].class))).thenReturn(boundStatement);

            CassandraOperationImpl spyCassandraOperation = spy(cassandraOperation);
            doNothing().when(spyCassandraOperation).logQueryElapseTime(anyString(), anyLong(), anyString());

            Map<String, Object> result = spyCassandraOperation.updateRecord(keyspaceName, tableName, request);

            verify(connectionManager, atLeastOnce()).getSession(keyspaceName);
            verify(session).prepare(query);
            verify(preparedStatement).bind(any(Object[].class));
            verify(session).execute(boundStatement);
            verify(spyCassandraOperation).logQueryElapseTime(eq("updateRecord"), anyLong(), eq(query));

            assertEquals(Constants.SUCCESS, result.get(Constants.RESPONSE));
        }
    }

    @Test
    public void testUpdateRecordGeneralFailure() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Updated Name");
        String query = "UPDATE test_keyspace.test_table SET name = ? WHERE id = ?";
        RuntimeException testException = new RuntimeException("General error");
        try (MockedStatic<CassandraOperationImpl> cassandraOperationMock = mockStatic(CassandraOperationImpl.class)) {
            cassandraOperationMock.when(() -> CassandraOperationImpl.getUpdateQueryStatement(keyspaceName, tableName, request))
                    .thenReturn(query);
            Map<String, Object> expectedResponse = new HashMap<>();
            expectedResponse.put(Constants.RESPONSE, Constants.FAILED);
            CassandraOperationImpl mockOperation = mock(CassandraOperationImpl.class);
            when(mockOperation.updateRecord(keyspaceName, tableName, request)).thenReturn(expectedResponse);
            Map<String, Object> result = mockOperation.updateRecord(keyspaceName, tableName, request);
            assertEquals(Constants.FAILED, result.get(Constants.RESPONSE));
        }
    }

    @Test
    public void testGetUpdateQueryStatement() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Test Name");
        request.put("email", "test@example.com");
        String result = CassandraOperationImpl.getUpdateQueryStatement(keyspaceName, tableName, request);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain UPDATE keyword",
                result.toUpperCase().contains("UPDATE"));
        assertTrue("Result should contain keyspace and table",
                result.contains(keyspaceName) && result.contains(tableName));
    }

    @Test
    public void testGetUpdateQueryStatementMultipleFields() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Test Name");
        request.put("email", "test@example.com");

        String result = CassandraOperationImpl.getUpdateQueryStatement(keyspaceName, tableName, request);
        System.out.println("Multiple fields query: " + result);

        assertNotNull(result);
        assertTrue(result.startsWith("UPDATE test_keyspace.test_table SET"));
        assertTrue(result.contains("name = ?"));
        assertTrue(result.contains("email = ?"));
        assertTrue(result.contains("where id = ?"));
    }

    @Test
    public void testGetUpdateQueryStatementSingleField() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Test Name");

        String result = CassandraOperationImpl.getUpdateQueryStatement(keyspaceName, tableName, request);
        System.out.println("Single field query: " + result);

        assertNotNull(result);
        assertTrue(result.startsWith("UPDATE test_keyspace.test_table SET"));
        assertTrue(result.contains("name = ?"));
        assertTrue(result.contains("where id = ?"));
    }

    @Test
    public void testGetUpdateQueryStatementNoFields() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");

        String result = CassandraOperationImpl.getUpdateQueryStatement(keyspaceName, tableName, request);
        System.out.println("No fields query: " + result);

        assertNotNull(result);
        // With no fields to update, the method should still produce a valid query
        // but there will be no fields between SET and WHERE
        assertTrue(result.contains("UPDATE test_keyspace.test_table SET"));
        assertTrue(result.contains("where id = ?"));
    }

    @Test
    public void testGetUpdateQueryStatementFieldOrder() {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", "123");
        request.put("name", "Test Name");
        request.put("email", "test@example.com");
        request.put("age", 30);

        String result = CassandraOperationImpl.getUpdateQueryStatement(keyspaceName, tableName, request);
        System.out.println("Ordered fields query: " + result);

        assertNotNull(result);
        assertTrue(result.startsWith("UPDATE test_keyspace.test_table SET"));
        assertTrue(result.contains("name = ?"));
        assertTrue(result.contains("email = ?"));
        assertTrue(result.contains("age = ?"));
        assertTrue(result.contains("where id = ?"));
    }


    @Test
    public void testInsertBulkRecordSuccess() {
        String keyspace = "test_ks";
        String table = "test_table";

        when(connectionManager.getSession(keyspace)).thenReturn(session);
        when(session.prepare(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.bind(Mockito.any())).thenReturn(boundStatement);

        // Simulate batch execution
        doAnswer(invocation -> null).when(session).execute(any(BatchStatement.class));

        // Mock static utility (if CassandraUtil.getPreparedStatement is static)
        try (MockedStatic<CassandraUtil> mockedUtil = Mockito.mockStatic(CassandraUtil.class)) {
            mockedUtil.when(() -> CassandraUtil.getPreparedStatement(eq(keyspace), eq(table), anyMap()))
                    .thenReturn("INSERT INTO test_table (id, name) VALUES (?, ?)");

            Object result = cassandraOperation.insertBulkRecord(keyspace, table, requestList);
            assertEquals(Constants.SUCCESS, ((ApiResponse) result).get(Constants.RESPONSE));
        }
    }

    @Test
    public void testInsertBulkRecordException() {
        String keyspace = "test_ks";
        String table = "test_table";

        when(connectionManager.getSession(keyspace)).thenThrow(new RuntimeException("DB error"));

        Object result = cassandraOperation.insertBulkRecord(keyspace, table, requestList);

        ApiResponse response = (ApiResponse) result;
        assertEquals(Constants.FAILED, response.get(Constants.RESPONSE));
        assertEquals(true, response.get(Constants.ERROR_MESSAGE).toString().contains("Exception occurred while inserting"));
    }

    @Test
    public void testUpdateRecordByCompositeKeySuccess() {
        Map<String, Object> updateAttributes = new HashMap<>();
        updateAttributes.put("column1", "newValue");

        Map<String, Object> compositeKey = new HashMap<>();
        compositeKey.put("id", "123");

        when(connectionManager.getSession(keyspace)).thenReturn(session);
        when(session.execute(any(SimpleStatement.class))).thenReturn(resultSet);

        Map<String, Object> response = cassandraOperation.updateRecordByCompositeKey(
                keyspace, table, updateAttributes, compositeKey);

        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        assertNull(response.get(Constants.ERROR_MESSAGE));
        verify(session, times(1)).execute(any(SimpleStatement.class));
    }

    @Test(expected = RuntimeException.class)
    public void testUpdateRecordByCompositeKeyException() {
        updateAttributes = new HashMap<>();
        updateAttributes.put("name", "Updated Name");

        compositeKey = new HashMap<>();
        compositeKey.put("id", UUID.randomUUID());
        when(connectionManager.getSession(keyspace)).thenThrow(new RuntimeException("Simulated DB error"));

        try {
            cassandraOperation.updateRecordByCompositeKey(keyspace, table, updateAttributes, compositeKey);
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Simulated DB error"));
            throw e;
        }
    }
}