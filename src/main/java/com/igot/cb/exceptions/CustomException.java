package com.igot.cb.exceptions;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class CustomException extends RuntimeException {
    private String code;
    private String message;
    private HttpStatus httpStatusCode;
    private String errorCode;
    private int responseCode;

    public CustomException() {
    }

    public CustomException(String code, String message, HttpStatus httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }


    public CustomException(String code, String message, int responseCode) {
        this.code = code;
        this.message = message;
        this.responseCode = responseCode;
    }

}
