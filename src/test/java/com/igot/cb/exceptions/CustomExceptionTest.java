package com.igot.cb.exceptions;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import static org.junit.Assert.*;

public class CustomExceptionTest {

    @Test
    public void testDefaultConstructorAndSetters() {
        CustomException exception = new CustomException();
        exception.setCode("ERR001");
        exception.setMessage("Something went wrong");
        exception.setHttpStatusCode(HttpStatus.BAD_REQUEST);
        exception.setErrorCode("E001");
        exception.setResponseCode(400);

        assertEquals("ERR001", exception.getCode());
        assertEquals("Something went wrong", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatusCode());
        assertEquals("E001", exception.getErrorCode());
        assertEquals(400, exception.getResponseCode());
    }

    @Test
    public void testConstructorWithHttpStatus() {
        CustomException exception = new CustomException("ERR002", "Invalid input", HttpStatus.UNPROCESSABLE_ENTITY);

        assertEquals("ERR002", exception.getCode());
        assertEquals("Invalid input", exception.getMessage());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getHttpStatusCode());
        assertNull(exception.getErrorCode());
        assertEquals(0, exception.getResponseCode());
    }

    @Test
    public void testConstructorWithResponseCode() {
        CustomException exception = new CustomException("ERR003", "Timeout error", 504);

        assertEquals("ERR003", exception.getCode());
        assertEquals("Timeout error", exception.getMessage());
        assertNull(exception.getHttpStatusCode());
        assertEquals(504, exception.getResponseCode());
        assertNull(exception.getErrorCode());
    }
}