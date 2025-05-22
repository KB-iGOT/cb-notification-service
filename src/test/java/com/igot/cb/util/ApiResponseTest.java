package com.igot.cb.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testDefaultConstructor() {
        ApiResponse apiResponse = new ApiResponse();
        assertNull(apiResponse.getId(), "Id should be null for default constructor");
        assertEquals("v1", apiResponse.getVer(), "Version should be v1");
        assertNotNull(apiResponse.getTs(), "Timestamp should not be null");
        assertNotNull(apiResponse.getParams(), "Params should not be null");
        assertNotNull(apiResponse.getResult(), "Result map should not be empty");
        assertTrue(apiResponse.getResult().isEmpty(), "Result map should be empty");
        assertNull(apiResponse.getResponseCode(), "ResponseCode should be null");
    }

    @Test
    void testParameterizedConstructor() {
        String id = "api.test.id";
        ApiResponse apiResponse = new ApiResponse(id);
        assertEquals(id, apiResponse.getId(), "Id should match the one passed in constructor");
        assertEquals("v1", apiResponse.getVer(), "Version should be v1");
        assertNotNull(apiResponse.getTs(), "Timestamp should not be null");
        assertNotNull(apiResponse.getParams(), "Params should not be null");
        assertNotNull(apiResponse.getResult(), "Result map should not be empty");
        assertTrue(apiResponse.getResult().isEmpty(), "Result map should be empty");
        assertNull(apiResponse.getResponseCode(), "ResponseCode should be null");
    }

    @Test
    void testSettersAndGetters() {
        ApiResponse apiResponse = new ApiResponse();
        String id = "api.test.updated";
        String ver = "v2";
        String ts = new Timestamp(System.currentTimeMillis()).toString();
        ApiRespParam params = new ApiRespParam("test-msg-id");
        HttpStatus responseCode = HttpStatus.OK;
        apiResponse.setId(id);
        apiResponse.setVer(ver);
        apiResponse.setTs(ts);
        apiResponse.setParams(params);
        apiResponse.setResponseCode(responseCode);
        assertEquals(id, apiResponse.getId(), "Id should be updated");
        assertEquals(ver, apiResponse.getVer(), "Version should be updated");
        assertEquals(ts, apiResponse.getTs(), "Timestamp should be updated");
        assertEquals(params, apiResponse.getParams(), "Params should be updated");
        assertEquals(responseCode, apiResponse.getResponseCode(), "ResponseCode should be updated");
    }

    @Test
    void testPutMethod() {
        ApiResponse apiResponse = new ApiResponse();
        String key = "testKey";
        String value = "testValue";
        apiResponse.put(key, value);
        assertEquals(value, apiResponse.getResult().get(key), "Result map should contain the added entry");
        assertFalse(apiResponse.getResult().isEmpty(), "Result map should not be empty after adding an entry");
    }

    @Test
    void testGetResultMethod() {
        ApiResponse apiResponse = new ApiResponse();
        String key = "testKey";
        String value = "testValue";
        Map<String, Object> result = apiResponse.getResult();
        result.put(key, value);
        assertEquals(value, apiResponse.getResult().get(key), "Result map should contain the added entry");
    }

    @Test
    void testTimestampFormat() {
        ApiResponse apiResponse = new ApiResponse();
        String ts = apiResponse.getTs();
        assertDoesNotThrow(() -> Timestamp.valueOf(ts), "Timestamp should be in a valid format");
    }

    @Test
    void testParamsInitialization() {
        ApiResponse apiResponse = new ApiResponse();
        ApiRespParam params = apiResponse.getParams();

        assertNotNull(params, "Params should not be null");
        assertNotNull(params.getResMsgId(), "Params should have a resMsgId");
        assertEquals(params.getResMsgId(), params.getMsgId(), "resMsgId and msgId should be equal");
        assertNull(params.getErr(), "Error should be null by default");
        assertNull(params.getErrMsg(), "Error message should be null by default");
        assertNull(params.getStatus(), "Status should be null by default");
    }

    @Test
    void testMultiplePutOperations() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.put("key1", "value1");
        apiResponse.put("key2", 123);
        apiResponse.put("key3", true);
        assertEquals(3, apiResponse.getResult().size(), "Result map should contain 3 entries");
        assertEquals("value1", apiResponse.getResult().get("key1"), "First value should match");
        assertEquals(123, apiResponse.getResult().get("key2"), "Second value should match");
        assertEquals(true, apiResponse.getResult().get("key3"), "Third value should match");
    }
}