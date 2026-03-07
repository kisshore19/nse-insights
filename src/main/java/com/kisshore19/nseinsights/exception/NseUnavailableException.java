package com.kisshore19.nseinsights.exception;

public class NseUnavailableException extends RuntimeException {
    public NseUnavailableException(String message) {
        super(message);
    }
    public NseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
