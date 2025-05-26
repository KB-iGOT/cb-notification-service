package com.igot.cb.transactional.cassandrautils;

import org.junit.Test;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;

import static org.junit.Assert.assertEquals;

public class CassandraConfigTest {

    /**
     * Test implementation of the abstract CassandraConfig class
     */
    private static class TestCassandraConfig extends CassandraConfig {
        @Override
        public String getLocalDataCenter() {
            return "datacenter1";
        }
    }

    @Test
    public void testContactPointsGetterSetter() {
        TestCassandraConfig config = new TestCassandraConfig();
        String contactPoints = "localhost";
        config.setContactPoints(contactPoints);
        assertEquals(contactPoints, config.getContactPoints());
    }

    @Test
    public void testPortGetterSetter() {
        TestCassandraConfig config = new TestCassandraConfig();
        int port = 9042;
        config.setPort(port);
        assertEquals(port, config.getPort());
    }

    @Test
    public void testKeyspaceNameGetterSetter() {
        TestCassandraConfig config = new TestCassandraConfig();
        String keyspaceName = "mykeyspace";
        config.setKeyspaceName(keyspaceName);
        assertEquals(keyspaceName, config.getKeyspaceName());
    }

    @Test
    public void testInheritanceFromAbstractCassandraConfiguration() {
        TestCassandraConfig config = new TestCassandraConfig();
        assertEquals(true, config instanceof AbstractCassandraConfiguration);
    }
}