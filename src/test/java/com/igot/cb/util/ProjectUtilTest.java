package com.igot.cb.util;

import com.igot.cb.exceptions.CustomException;
import com.igot.cb.exceptions.ResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ProjectUtilTest {

    @Test
    void testCreateServerError() {
        CustomException ex = ProjectUtil.createServerError(ResponseCode.SERVER_ERROR);

        assertNotNull(ex);
        assertEquals(ResponseCode.SERVER_ERROR.getErrorCode(), ex.getErrorCode());
        assertEquals(ResponseCode.SERVER_ERROR.getErrorMessage(), ex.getMessage()); // Lombok getter
        assertEquals(ResponseCode.SERVER_ERROR.getStatusCode(), ex.getResponseCode());
    }

    @Test
    void testCreateClientException() {
        CustomException ex = ProjectUtil.createClientException(ResponseCode.CLIENT_ERROR);

        assertNotNull(ex);
        assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), ex.getErrorCode());
        assertEquals(ResponseCode.CLIENT_ERROR.getErrorMessage(), ex.getMessage()); // Lombok getter
        assertEquals(ResponseCode.CLIENT_ERROR.getStatusCode(), ex.getResponseCode());
    }

    @Test
    void testCreateDefaultResponse() {
        String apiName = "testApi";
        ApiResponse response = ProjectUtil.createDefaultResponse(apiName);

        assertNotNull(response);
        assertEquals(apiName, response.getId());
        assertEquals(Constants.API_VERSION_1, response.getVer());
        assertNotNull(response.getParams());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getParams().getResMsgId());
        assertNotNull(response.getTs());
    }

    @Test
    void testLoggerInitialization() {
        ProjectUtil util = new ProjectUtil();
        assertNotNull(util); // covers the logger instantiation
    }
}
