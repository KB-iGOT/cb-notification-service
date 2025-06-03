package com.igot.cb.transactional.cassandrautils;


import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.igot.cb.util.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CassandraUtilTest {

    @Test
    public void testGetPreparedStatement_WithSingleField() {
        // Arrange
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> map = new LinkedHashMap<>(); // Using LinkedHashMap for predictable order
        map.put("id", "123");

        // Act
        String result = CassandraUtil.getPreparedStatement(keyspaceName, tableName, map);

        // Assert
        String expected = Constants.INSERT_INTO + keyspaceName + Constants.DOT + tableName +
                Constants.OPEN_BRACE + "id" + Constants.VALUES_WITH_BRACE +
                Constants.QUE_MARK + Constants.CLOSING_BRACE;
        assertEquals(expected, result);
    }

    @Test
    public void testGetPreparedStatement_WithMultipleFields() {
        // Arrange
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> map = new LinkedHashMap<>(); // Using LinkedHashMap for predictable order
        map.put("id", "123");
        map.put("name", "Test User");
        map.put("age", 30);

        // Act
        String result = CassandraUtil.getPreparedStatement(keyspaceName, tableName, map);

        // Assert
        String expected = Constants.INSERT_INTO + keyspaceName + Constants.DOT + tableName +
                Constants.OPEN_BRACE + "id,name,age" + Constants.VALUES_WITH_BRACE +
                Constants.QUE_MARK + Constants.COMMA + Constants.QUE_MARK + Constants.COMMA +
                Constants.QUE_MARK + Constants.CLOSING_BRACE;
        assertEquals(expected, result);
    }

    @Test
    public void testGetPreparedStatement_WithEmptyMap() {
        // Arrange
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> map = new HashMap<>();

        // Act
        String result = CassandraUtil.getPreparedStatement(keyspaceName, tableName, map);

        // Assert
        String expected = Constants.INSERT_INTO + keyspaceName + Constants.DOT + tableName +
                Constants.OPEN_BRACE + "" + Constants.VALUES_WITH_BRACE +
                "" + Constants.CLOSING_BRACE;
        assertEquals(expected, result);
    }

    @Test
    public void testCreateResponse() {
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);
        Iterator<Row> rowIterator = Collections.singletonList(mockRow).iterator();
        when(mockResultSet.iterator()).thenReturn(rowIterator);
        Map<String, String> mockColumnsMapping = new HashMap<>();
        mockColumnsMapping.put("id", "id_column");
        mockColumnsMapping.put("name", "name_column");
        when(mockRow.getObject("id_column")).thenReturn(101);
        when(mockRow.getObject("name_column")).thenReturn("Alice");
        try (MockedStatic<CassandraUtil> mockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            mockedStatic.when(() -> CassandraUtil.fetchColumnsMapping(mockResultSet))
                    .thenReturn(mockColumnsMapping);
            mockedStatic.when(() -> CassandraUtil.createResponse(mockResultSet))
                    .thenCallRealMethod();
            List<Map<String, Object>> result = CassandraUtil.createResponse(mockResultSet);
            assertEquals(1, result.size());
            Map<String, Object> rowMap = result.get(0);
            assertEquals(101, rowMap.get("id"));
            assertEquals("Alice", rowMap.get("name"));
        }
    }

    @Test
    public void testCreateResponse_WithKey() {
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);
        Iterator<Row> rowIterator = Collections.singletonList(mockRow).iterator();
        when(mockResultSet.iterator()).thenReturn(rowIterator);
        Map<String, String> columnMapping = new HashMap<>();
        columnMapping.put("userId", "user_id_column");
        columnMapping.put("email", "email_column");
        when(mockRow.getObject("user_id_column")).thenReturn("123");
        when(mockRow.getObject("email_column")).thenReturn("test@example.com");
        try (MockedStatic<CassandraUtil> staticMock = mockStatic(CassandraUtil.class)) {
            staticMock.when(() -> CassandraUtil.fetchColumnsMapping(mockResultSet))
                    .thenReturn(columnMapping);
            staticMock.when(() -> CassandraUtil.createResponse(mockResultSet, "userId"))
                    .thenCallRealMethod();
            Map<String, Object> result = CassandraUtil.createResponse(mockResultSet, "userId");
            assertEquals(1, result.size());
            assertTrue(result.containsKey("123"));
            Map<String, Object> rowMap = (Map<String, Object>) result.get("123");
            assertEquals("123", rowMap.get("userId"));
            assertEquals("test@example.com", rowMap.get("email"));
        }
    }

    @Test
    public void testFetchColumnsMapping() {
        // Mock ColumnMetadata
        ColumnMetadata column1 = mock(ColumnMetadata.class);
        ColumnMetadata column2 = mock(ColumnMetadata.class);

        // Mock ColumnDefinitions
        ColumnDefinitions columnDefinitions = mock(ColumnDefinitions.class);

        // Mock ResultSet
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        // Call the method
        Map<String, String> result = CassandraUtil.fetchColumnsMapping(resultSet);

        // Assert the mapping
        assertEquals(0, result.size());

    }
}