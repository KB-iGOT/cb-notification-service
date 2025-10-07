package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseCodeTest {

    @Test
    void testGetResponse_withUnauthorizedCode() {
        ResponseCode code = ResponseCode.getResponse(Constants.UNAUTHORIZED);
        assertEquals(ResponseCode.UNAUTHORIZED, code);
    }

    @Test
    void testGetResponse_withKnownErrorCode() {
        // The errorCode for unAuthorized is ResponseMessage.Key.UNAUTHORIZED_USER
        String knownCode = ResponseMessage.Key.UNAUTHORIZED_USER;
        ResponseCode code = ResponseCode.getResponse(knownCode);
        assertEquals(ResponseCode.UNAUTHORIZED, code);

        // The errorCode for internalError is ResponseMessage.Key.INTERNAL_ERROR
        knownCode = ResponseMessage.Key.INTERNAL_ERROR;
        code = ResponseCode.getResponse(knownCode);
        assertEquals(ResponseCode.INTERNAL_ERROR, code);
    }

    @Test
    void testGetResponse_withBlankCode() {
        assertNull(ResponseCode.getResponse(""));
        assertNull(ResponseCode.getResponse(null));
    }

    @Test
    void testEnumValues_forOKAndErrors() {
        assertEquals(200, ResponseCode.OK.getStatusCode());
        assertEquals(400, ResponseCode.CLIENT_ERROR.getStatusCode());
        assertEquals(500, ResponseCode.SERVER_ERROR.getStatusCode());
        // errorCode and errorMessage are null for these
        assertNull(ResponseCode.OK.getErrorCode());
        assertNull(ResponseCode.OK.getErrorMessage());
    }

    @Test
    void testGetErrorMessage_returnsExpectedMessage() {
        assertEquals("You are not authorized.", ResponseCode.UNAUTHORIZED.getErrorMessage());
        assertNull(ResponseCode.OK.getErrorMessage());
    }

    @Test
    void testErrorCodeAndMessageForUnAuthorized() {
        assertEquals(ResponseMessage.Key.UNAUTHORIZED_USER, ResponseCode.UNAUTHORIZED.getErrorCode());
        assertEquals(ResponseMessage.Message.UNAUTHORIZED_USER, ResponseCode.UNAUTHORIZED.getErrorMessage());
    }

    @Test
    void testErrorCodeAndMessageForInternalError() {
        assertEquals(ResponseMessage.Key.INTERNAL_ERROR, ResponseCode.INTERNAL_ERROR.getErrorCode());
        assertEquals(ResponseMessage.Message.INTERNAL_ERROR, ResponseCode.INTERNAL_ERROR.getErrorMessage());
    }
}