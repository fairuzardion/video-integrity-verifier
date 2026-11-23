package com.noexperience.integrity.common;

/** Base class for all domain errors raised by the verifier. */
public class IntegrityException extends RuntimeException {
    public IntegrityException(String message) {
        super(message);
    }

    public IntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
