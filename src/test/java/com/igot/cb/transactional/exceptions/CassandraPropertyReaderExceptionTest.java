package com.igot.cb.transactional.exceptions;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test class for CassandraPropertyReaderException
 */
public class CassandraPropertyReaderExceptionTest {

    @Test
    public void testCassandraPropertyReaderExceptionConstructor() {
        String expectedMessage = "Test error message";
        Throwable expectedCause = new IllegalArgumentException("Test cause");
        CassandraPropertyReaderException exception = new CassandraPropertyReaderException(expectedMessage, expectedCause);
        assertEquals("Exception message should match", expectedMessage, exception.getMessage());
        assertEquals("Exception cause should match", expectedCause, exception.getCause());
    }

    @Test
    public void testExceptionWithNullMessage() {
        String expectedMessage = null;
        Throwable expectedCause = new RuntimeException("Some cause");
        CassandraPropertyReaderException exception = new CassandraPropertyReaderException(expectedMessage, expectedCause);
        assertEquals("Exception message should be null", expectedMessage, exception.getMessage());
        assertEquals("Exception cause should match", expectedCause, exception.getCause());
    }

    @Test
    public void testExceptionWithNullCause() {
        String expectedMessage = "Error reading Cassandra properties";
        Throwable expectedCause = null;
        CassandraPropertyReaderException exception = new CassandraPropertyReaderException(expectedMessage, expectedCause);
        assertEquals("Exception message should match", expectedMessage, exception.getMessage());
        assertEquals("Exception cause should be null", expectedCause, exception.getCause());
    }

    @Test
    public void testConstructor_withMessageAndCause() {
        String expectedMessage = "Test error message";
        Throwable expectedCause = new IllegalArgumentException("Test cause");
        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException(expectedMessage, expectedCause);
        assertEquals("Exception message should match", expectedMessage, exception.getMessage());
        assertSame("Exception cause should match", expectedCause, exception.getCause());
    }

    @Test
    public void testConstructor_withNullMessageAndValidCause() {
        Throwable expectedCause = new RuntimeException("Some cause");

        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException(null, expectedCause);

        assertNull("Exception message should be null", exception.getMessage());
        assertSame("Exception cause should match", expectedCause, exception.getCause());
    }

    @Test
    public void testConstructor_withMessageAndNullCause() {
        String expectedMessage = "Error reading Cassandra properties";

        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException(expectedMessage, null);

        assertEquals("Exception message should match", expectedMessage, exception.getMessage());
        assertNull("Exception cause should be null", exception.getCause());
    }

    @Test
    public void testConstructor_withNullMessageAndNullCause() {
        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException(null, null);

        assertNull("Exception message should be null", exception.getMessage());
        assertNull("Exception cause should be null", exception.getCause());
    }
}