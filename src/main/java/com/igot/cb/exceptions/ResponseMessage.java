package com.igot.cb.exceptions;

/**
 * This class holds all the response keys and messages as constants.
 */
public final class ResponseMessage {

    private ResponseMessage() {
    }

    public static final class Message {
        public static final String UNAUTHORIZED_USER = "You are not authorized.";
        public static final String INTERNAL_ERROR = "Process failed,please try again later.";
        private Message() {
        }
    }

    public static final class Key {
        public static final String UNAUTHORIZED_USER = "UNAUTHORIZED_USER";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        private Key() {
        }
    }
}