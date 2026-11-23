package com.noexperience.integrity.common;

/** The caller supplied invalid input (bad parameters, malformed manifest, missing file ...). */
public class InvalidInputException extends IntegrityException {
    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
