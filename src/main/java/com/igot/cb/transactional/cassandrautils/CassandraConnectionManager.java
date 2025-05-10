package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;

import java.util.List;

/**
 * @author Mahesh RV
 * @author Ruksana
 */
public interface CassandraConnectionManager {

    /**
     * Retrieves a Cassandra session for the specified keyspace.
     *
     * @param keyspaceName The name of the keyspace for which to retrieve the session.
     * @return A Cassandra Session object for interacting with the specified keyspace.
     */
    CqlSession getSession(String keyspaceName);

    List<String> getTableList(String keyspacename);

}