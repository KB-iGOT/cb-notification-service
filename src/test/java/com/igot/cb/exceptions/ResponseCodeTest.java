package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseCodeTest {

    @Test
    void testGetResponse_withUnauthorizedCode() {
        ResponseCode code = ResponseCode.getResponse(Constants.UNAUTHORIZED);
        assertEquals(ResponseCode.unAuthorized, code);
    }

    @Test
    void testGetResponse_withKnownErrorCode() {
        // The errorCode for unAuthorized is ResponseMessage.Key.UNAUTHORIZED_USER
        String knownCode = ResponseMessage.Key.UNAUTHORIZED_USER;
        ResponseCode code = ResponseCode.getResponse(knownCode);
        assertEquals(ResponseCode.unAuthorized, code);

        // The errorCode for internalError is ResponseMessage.Key.INTERNAL_ERROR
        knownCode = ResponseMessage.Key.INTERNAL_ERROR;
        code = ResponseCode.getResponse(knownCode);
        assertEquals(ResponseCode.internalError, code);
    }

    @Test
    void testGetResponse_withBlankCode() {
        assertNull(ResponseCode.getResponse(""));
        assertNull(ResponseCode.getResponse(null));
    }

    @Test
    void testEnumValues_forOKAndErrors() {
        assertEquals(200, ResponseCode.OK.getResponseCode());
        assertEquals(400, ResponseCode.CLIENT_ERROR.getResponseCode());
        assertEquals(500, ResponseCode.SERVER_ERROR.getResponseCode());
        // errorCode and errorMessage are null for these
        assertNull(ResponseCode.OK.getErrorCode());
        assertNull(ResponseCode.OK.getErrorMessage());
    }

    @Test
    void testGetMessage_alwaysReturnsEmptyString() {
        assertEquals("", ResponseCode.unAuthorized.getMessage(401));
        assertEquals("", ResponseCode.OK.getMessage(200));
    }

    @Test
    void testErrorCodeAndMessageForUnAuthorized() {
        assertEquals(ResponseMessage.Key.UNAUTHORIZED_USER, ResponseCode.unAuthorized.getErrorCode());
        assertEquals(ResponseMessage.Message.UNAUTHORIZED_USER, ResponseCode.unAuthorized.getErrorMessage());
    }

    @Test
    void testErrorCodeAndMessageForInternalError() {
        assertEquals(ResponseMessage.Key.INTERNAL_ERROR, ResponseCode.internalError.getErrorCode());
        assertEquals(ResponseMessage.Message.INTERNAL_ERROR, ResponseCode.internalError.getErrorMessage());
    }
}