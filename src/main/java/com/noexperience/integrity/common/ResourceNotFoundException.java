package com.noexperience.integrity.common;

/** A requested manifest or report does not exist. */
public class ResourceNotFoundException extends IntegrityException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
