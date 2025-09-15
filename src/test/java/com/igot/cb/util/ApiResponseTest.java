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
        assertNull(apiResponse.getId());
        assertEquals("v1", apiResponse.getVer());
        assertNotNull(apiResponse.getTs());
        assertNotNull(apiResponse.getParams());
        assertNotNull(apiResponse.getResult());
        assertTrue(apiResponse.getResult().isEmpty());
        assertNull(apiResponse.getResponseCode());
    }

    @Test
    void testParameterizedConstructor() {
        String id = "api.test.id";
        ApiResponse apiResponse = new ApiResponse(id);
        assertEquals(id, apiResponse.getId());
        assertEquals("v1", apiResponse.getVer());
        assertNotNull(apiResponse.getTs());
        assertNotNull(apiResponse.getParams());
        assertNotNull(apiResponse.getResult());
        assertTrue(apiResponse.getResult().isEmpty());
        assertNull(apiResponse.getResponseCode());
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
        assertEquals(id, apiResponse.getId());
        assertEquals(ver, apiResponse.getVer());
        assertEquals(ts, apiResponse.getTs());
        assertEquals(params, apiResponse.getParams());
        assertEquals(responseCode, apiResponse.getResponseCode());
    }

    @Test
    void testPutMethod() {
        ApiResponse apiResponse = new ApiResponse();
        String key = "testKey";
        String value = "testValue";
        apiResponse.put(key, value);
        assertEquals(value, apiResponse.getResult().get(key));
        assertFalse(apiResponse.getResult().isEmpty());
    }

    @Test
    void testGetResultMethod() {
        ApiResponse apiResponse = new ApiResponse();
        String key = "testKey";
        String value = "testValue";
        Map<String, Object> result = apiResponse.getResult();
        result.put(key, value);
        assertEquals(value, apiResponse.getResult().get(key));
    }

    @Test
    void testTimestampFormat() {
        ApiResponse apiResponse = new ApiResponse();
        String ts = apiResponse.getTs();
        assertDoesNotThrow(() -> Timestamp.valueOf(ts));
    }

    @Test
    void testParamsInitialization() {
        ApiResponse apiResponse = new ApiResponse();
        ApiRespParam params = apiResponse.getParams();
        assertNotNull(params);
        assertNotNull(params.getResMsgId());
        assertEquals(params.getResMsgId(), params.getMsgId());
        assertNull(params.getErr());
        assertNull(params.getErrMsg());
        assertNull(params.getStatus());
    }

    @Test
    void testMultiplePutOperations() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.put("key1", "value1");
        apiResponse.put("key2", 123);
        apiResponse.put("key3", true);
        assertEquals(3, apiResponse.getResult().size());
        assertEquals("value1", apiResponse.getResult().get("key1"));
        assertEquals(123, apiResponse.getResult().get("key2"));
        assertEquals(true, apiResponse.getResult().get("key3"));
    }

    @Test
    void testSetResultReplacesResponse() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.put("oldKey", "oldValue");
        Map<String, Object> newResult = new HashMap<>();
        newResult.put("newKey", "newValue");
        apiResponse.setResult(newResult);
        assertEquals("newValue", apiResponse.get("newKey"));
        assertNull(apiResponse.get("oldKey"));
    }

    @Test
    void testGetReturnsNullForMissingKey() {
        ApiResponse apiResponse = new ApiResponse();
        assertNull(apiResponse.get("nonexistentKey"));
    }

    @Test
    void testEqualsAndHashCode_sameObject() {
        ApiResponse r1 = new ApiResponse("id1");
        assertEquals(r1, r1);
        assertEquals(r1.hashCode(), r1.hashCode());
    }

    @Test
    void testEquals_notEqualDifferentId() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id2");
        assertNotEquals(r1, r2);
    }

    @Test
    void testEquals_notEqualNull() {
        ApiResponse r1 = new ApiResponse("id1");
        assertNotEquals(r1, null);
    }

    @Test
    void testEquals_notEqualDifferentClass() {
        ApiResponse r1 = new ApiResponse("id1");
        assertNotEquals(r1, "someString");
    }

    @Test
    void testToStringContainsId() {
        ApiResponse r1 = new ApiResponse("id1");
        String str = r1.toString();
        assertTrue(str.contains("id1"));
    }

    @Test
    void testEquals_idNullVsNonNull() {
        ApiResponse r1 = new ApiResponse();
        ApiResponse r2 = new ApiResponse();
        r1.setId(null);
        r2.setId("id2");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        assertNotEquals(r1, r2);
    }

    @Test
    void testEquals_responseCodeMismatch() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        r1.setResponseCode(HttpStatus.OK);
        r2.setResponseCode(HttpStatus.BAD_REQUEST);
        assertNotEquals(r1, r2);
    }

    @Test
    void testEquals_equalObjects() {
        ApiRespParam params = new ApiRespParam("msg-123");
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        r1.setVer("v1");
        r2.setVer("v1");
        r1.setParams(params);
        r2.setParams(params);
        r1.setResponseCode(null);
        r2.setResponseCode(null);
        r1.setResult(new HashMap<>());
        r2.setResult(new HashMap<>());
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEquals_bothIdsNull() {
        ApiRespParam sharedParams = new ApiRespParam("same-msg-id");
        ApiResponse r1 = new ApiResponse();
        ApiResponse r2 = new ApiResponse();
        r1.setId(null);
        r2.setId(null);
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        r1.setVer("v1");
        r2.setVer("v1");
        r1.setParams(sharedParams);
        r2.setParams(sharedParams);
        r1.setResult(new HashMap<>());
        r2.setResult(new HashMap<>());
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEquals_nullVsNonNullVer() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        r1.setVer(null);
        r2.setVer("v2");
        assertNotEquals(r1, r2);
    }

    @Test
    void testEquals_nullVsNonNullTs() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        r1.setTs(null);
        r2.setTs("2025-09-15 10:00:00.0");
        assertNotEquals(r1, r2);
    }

    @Test
    void testEquals_paramsDifferentObjects() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        r1.setVer("v1");
        r2.setVer("v1");
        r1.setParams(new ApiRespParam("msg1"));
        r2.setParams(new ApiRespParam("msg2"));
        assertNotEquals(r1, r2);
    }

    @Test
    void testEquals_bothResponseNull() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        r1.setVer("v1");
        r2.setVer("v1");
        ApiRespParam sharedParams = new ApiRespParam("msg");
        r1.setParams(sharedParams);
        r2.setParams(sharedParams);
        r1.setResult(null);
        r2.setResult(null);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testEquals_nullComparison() {
        ApiResponse r1 = new ApiResponse("id1");
        assertNotEquals(r1, null);
    }

    @Test
    void testEquals_differentClass() {
        ApiResponse r1 = new ApiResponse("id1");
        assertNotEquals(r1, "string");
    }

    @Test
    void testEquals_verBothNull() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        ApiRespParam sharedParams = new ApiRespParam("msg");
        r1.setParams(sharedParams);
        r2.setParams(sharedParams);
        r1.setVer(null);
        r2.setVer(null);
        assertEquals(r1, r2);
    }

    @Test
    void testEquals_responseCodeNullVsNonNull() {
        ApiResponse r1 = new ApiResponse("id1");
        ApiResponse r2 = new ApiResponse("id1");
        String fixedTs = "2025-09-15 10:00:00.0";
        r1.setTs(fixedTs);
        r2.setTs(fixedTs);
        ApiRespParam sharedParams = new ApiRespParam("msg");
        r1.setParams(sharedParams);
        r2.setParams(sharedParams);
        r1.setResponseCode(null);
        r2.setResponseCode(HttpStatus.OK);
        assertNotEquals(r1, r2);
    }
}
