package com.igot.cb.model;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiRespParamTest {

    @Test
    void testDefaultConstructorAndSettersGetters() {
        ApiRespParam param = new ApiRespParam();
        param.setMsgid("res123");
        param.setErr("ERR_01");
        param.setStatus("FAILED");

        assertEquals("res123", param.getMsgid());
        assertEquals("ERR_01", param.getErr());
        assertEquals("FAILED", param.getStatus());
    }

    @Test
    void testParameterizedConstructor() {
        ApiRespParam param = new ApiRespParam("id123");

        assertEquals("id123", param.getMsgid());

        // Other fields should be null
        assertNull(param.getErr());
        assertNull(param.getStatus());
    }
}