package com.igot.cb.util;

import com.igot.cb.exceptions.CustomException;
import com.igot.cb.exceptions.ResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import java.util.*;

/**
 * This class will contains all the common utility methods.
 */
public class ProjectUtil {

//    private final Logger logger = LoggerFactory.getLogger(ExtendedServiceImpl.class);
private final Logger logger = LoggerFactory.getLogger(Object.class);

    /**
     * This method will create and return server exception to caller.
     *
     * @param responseCode ResponseCode
     * @return ProjectCommonException
     */
    public static CustomException createServerError(ResponseCode responseCode) {
        return new CustomException(responseCode.getErrorCode(), responseCode.getErrorMessage(),
                ResponseCode.SERVER_ERROR.getResponseCode());
    }

    public static CustomException createClientException(ResponseCode responseCode) {
        return new CustomException(responseCode.getErrorCode(), responseCode.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    public static ApiResponse createDefaultResponse(String api) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(Constants.API_VERSION_1);
        response.setParams(new ApiRespParam(UUID.randomUUID().toString()));
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(java.time.LocalDateTime.now().toString());
        return response;
    }
}
