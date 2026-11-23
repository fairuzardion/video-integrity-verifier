package com.noexperience.integrity.common;

/** Key loading, key generation, signing or verification infrastructure failed. */
public class CryptoOperationException extends IntegrityException {
    public CryptoOperationException(String message) {
        super(message);
    }

    public CryptoOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
