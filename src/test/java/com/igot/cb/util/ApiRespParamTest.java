package com.igot.cb.util;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.UUID;

public class ApiRespParamTest {

    @Test
    public void testDefaultConstructor() {
        ApiRespParam params = new ApiRespParam();
        assertNull("ResMsgId should be null for default constructor", params.getResMsgId());
        assertNull("MsgId should be null for default constructor", params.getMsgId());
        assertNull("Err should be null for default constructor", params.getErr());
        assertNull("Status should be null for default constructor", params.getStatus());
        assertNull("ErrMsg should be null for default constructor", params.getErrMsg());
    }
    
    @Test
    public void testParameterizedConstructor() {
        String id = UUID.randomUUID().toString();
        ApiRespParam params = new ApiRespParam(id);
        assertEquals("ResMsgId should match the provided ID", id, params.getResMsgId());
        assertEquals("MsgId should match the provided ID", id, params.getMsgId());
        assertNull("Err should be null initially", params.getErr());
        assertNull("Status should be null initially", params.getStatus());
        assertNull("ErrMsg should be null initially", params.getErrMsg());
    }
    
    @Test
    public void testSettersAndGetters() {
        ApiRespParam params = new ApiRespParam();
        String resMsgId = "res-123";
        String msgId = "msg-456";
        String err = "ERR_001";
        String status = "FAILED";
        String errMsg = "An error occurred";
        params.setResMsgId(resMsgId);
        params.setMsgId(msgId);
        params.setErr(err);
        params.setStatus(status);
        params.setErrMsg(errMsg);
        assertEquals("ResMsgId should be updated", resMsgId, params.getResMsgId());
        assertEquals("MsgId should be updated", msgId, params.getMsgId());
        assertEquals("Err should be updated", err, params.getErr());
        assertEquals("Status should be updated", status, params.getStatus());
        assertEquals("ErrMsg should be updated", errMsg, params.getErrMsg());
    }
    
    @Test
    public void testWithNullValues() {
        ApiRespParam params = new ApiRespParam("test-id");
        params.setResMsgId(null);
        params.setMsgId(null);
        params.setErr(null);
        params.setStatus(null);
        params.setErrMsg(null);
        assertNull("ResMsgId should be set to null", params.getResMsgId());
        assertNull("MsgId should be set to null", params.getMsgId());
        assertNull("Err should be set to null", params.getErr());
        assertNull("Status should be set to null", params.getStatus());
        assertNull("ErrMsg should be set to null", params.getErrMsg());
    }
    
    @Test
    public void testEmptyStringValues() {
        ApiRespParam params = new ApiRespParam();
        String emptyString = "";
        params.setResMsgId(emptyString);
        params.setMsgId(emptyString);
        params.setErr(emptyString);
        params.setStatus(emptyString);
        params.setErrMsg(emptyString);
        assertEquals("ResMsgId should be set to empty string", emptyString, params.getResMsgId());
        assertEquals("MsgId should be set to empty string", emptyString, params.getMsgId());
        assertEquals("Err should be set to empty string", emptyString, params.getErr());
        assertEquals("Status should be set to empty string", emptyString, params.getStatus());
        assertEquals("ErrMsg should be set to empty string", emptyString, params.getErrMsg());
    }
}