package com.igot.cb.exceptions;

/**
 * This interface will hold all the response key and message
 */
public interface ResponseMessage {

    interface Message {

        String UNAUTHORIZED_USER = "You are not authorized.";
        String INTERNAL_ERROR = "Process failed,please try again later.";
    }

    interface Key {
        String UNAUTHORIZED_USER = "UNAUTHORIZED_USER";
        String INTERNAL_ERROR = "INTERNAL_ERROR";

    }
}