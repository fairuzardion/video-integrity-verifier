package com.noexperience.integrity.api;

/** Uniform error body returned by every failing endpoint. */
public record ApiError(String timestamp, int status, String error, String message, String path) {
}
