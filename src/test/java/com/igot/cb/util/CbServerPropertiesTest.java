package com.igot.cb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CbServerPropertiesTest {

    @Test
    void testAllGettersAndSetters() {
        CbServerProperties props = new CbServerProperties();

        props.setRedisInsightIndex(1);
        props.setSearchResultRedisTtl(1000L);
        props.setSbApiKey("dummy-api-key");
        props.setRequestTimeoutMs(5000);
        props.setMaxTotalConnections(100);
        props.setMaxConnectionsPerRoute(20);
        props.setRedisPoolMaxTotal(50);
        props.setRedisPoolMaxIdle(25);
        props.setRedisPoolMinIdle(5);
        props.setRedisPoolMaxWait(3000);
        props.setRedisConnectionTimeout(2000L);

        assertEquals(1, props.getRedisInsightIndex());
        assertEquals(1000L, props.getSearchResultRedisTtl());
        assertEquals("dummy-api-key", props.getSbApiKey());
        assertEquals(5000, props.getRequestTimeoutMs());
        assertEquals(100, props.getMaxTotalConnections());
        assertEquals(20, props.getMaxConnectionsPerRoute());
        assertEquals(50, props.getRedisPoolMaxTotal());
        assertEquals(25, props.getRedisPoolMaxIdle());
        assertEquals(5, props.getRedisPoolMinIdle());
        assertEquals(3000, props.getRedisPoolMaxWait());
        assertEquals(2000L, props.getRedisConnectionTimeout());
    }
}

