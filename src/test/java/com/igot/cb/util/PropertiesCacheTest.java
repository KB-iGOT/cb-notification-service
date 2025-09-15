package com.igot.cb.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PropertiesCacheTest {

    private PropertiesCache cache;

    @BeforeEach
    void setUp() {
        cache = PropertiesCache.getInstance();
    }

    private Properties getInternalProperties() throws Exception {
        Field field = PropertiesCache.class.getDeclaredField("configProp");
        field.setAccessible(true);
        return (Properties) field.get(cache);
    }

    @Test
    void testSingletonInstance() {
        PropertiesCache instance1 = PropertiesCache.getInstance();
        PropertiesCache instance2 = PropertiesCache.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "Both calls should return the same singleton instance");
    }

    @Test
    void testGetProperty_withConfigProperty() throws Exception {
        Properties props = getInternalProperties();
        props.setProperty("testKey", "testValue");

        String result = cache.getProperty("testKey");
        assertEquals("testValue", result);
    }

    @Test
    void testGetProperty_keyNotFound_returnsKey() {
        String key = "nonexistentKey";
        String result = cache.getProperty(key);
        assertEquals(key, result);
    }

    @Test
    void testReadProperty_withConfigProperty() throws Exception {
        Properties props = getInternalProperties();
        props.setProperty("readKey", "readValue");

        String result = cache.readProperty("readKey");
        assertEquals("readValue", result);
    }

    @Test
    void testReadProperty_keyNotFound_returnsNull() {
        String result = cache.readProperty("missingKey");
        assertNull(result);
    }
}
