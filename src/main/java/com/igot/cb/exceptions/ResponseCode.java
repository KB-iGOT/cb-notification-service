package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;


@Getter
public enum ResponseCode {
    unAuthorized(ResponseMessage.Key.UNAUTHORIZED_USER, ResponseMessage.Message.UNAUTHORIZED_USER),
    internalError(ResponseMessage.Key.INTERNAL_ERROR, ResponseMessage.Message.INTERNAL_ERROR),

    OK(200),
    CLIENT_ERROR(400),
    SERVER_ERROR(500);
    @Setter
    private int responseCode;
    /**
     * error code contains String value
     */
    private String errorCode;
    /**
     * errorMessage contains proper error message.
     */
    private String errorMessage;

    /**
     * @param errorCode    String
     * @param errorMessage String
     */
    ResponseCode(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    ResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    /**
     * This method will provide ResponseCode enum based on error code
     */
    public static ResponseCode getResponse(String errorCode) {
        if (StringUtils.isBlank(errorCode)) {
            return null;
        } else if (Constants.UNAUTHORIZED.equals(errorCode)) {
            return ResponseCode.unAuthorized;
        } else {
            ResponseCode value = null;
            ResponseCode[] responseCodes = ResponseCode.values();
            for (ResponseCode response : responseCodes) {
                if (response.getErrorCode().equals(errorCode)) {
                    return response;
                }
            }
            return value;
        }
    }

    public String getMessage(int errorCode) {
        return "";
    }

}