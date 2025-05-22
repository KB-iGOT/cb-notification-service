package com.igot.cb.transactional.cassandrautils;

import com.igot.cb.util.ApiResponse;

import java.util.List;
import java.util.Map;

/**
 * Interface defining Cassandra operations for querying records.
 */

public interface CassandraOperation {

    /**
     * Retrieves records from Cassandra based on specified properties and key.
     *
     * @param keyspaceName The name of the keyspace containing the table.
     * @param tableName    The name of the table from which to retrieve records.
     * @param propertyMap  A map representing properties to filter records.
     * @param fields       A list of fields to include in the retrieved records.
     * @param key          The key used for retrieving records (e.g., partition key).
     * @return A list of maps representing the retrieved records.
     */
    List<Map<String, Object>> getRecordsByPropertiesByKey(String keyspaceName, String tableName,
                                                          Map<String, Object> propertyMap, List<String> fields, String key);

    /**
     * Inserts a record into Cassandra.
     *
     * @param keyspaceName The name of the keyspace containing the table.
     * @param tableName    The name of the table into which to insert the record.
     * @param request      A map representing the record to insert.
     * @return An object representing the result of the insertion operation.
     */
    public Object insertRecord(String keyspaceName, String tableName, Map<String, Object> request);


    /**
     * Insert bulk data using batch
     *
     * @param keyspaceName String
     * @param tableName    String
     * @param request      List<Map<String, Object>>
     * @return SBApiResponse
     */
    public Object insertBulkRecord(String keyspaceName, String tableName, List<Map<String, Object>> request);

    public List<Map<String, Object>> getRecordsByPropertiesWithoutFiltering(String keyspaceName, String tableName,
                                                                            Map<String, Object> propertyMap, List<String> fields, Integer limit);

    public Map<String, Object> updateRecord(
            String keyspaceName, String tableName, Map<String, Object> request);

    public Map<String, Object> updateRecordByCompositeKey(
            String keyspaceName,
            String tableName,
            Map<String, Object> updateAttributes,
            Map<String, Object> compositeKey);
}
