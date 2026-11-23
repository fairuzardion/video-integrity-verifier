package com.noexperience.integrity.chain;

/** Outcome of a manifest signature verification with a human-readable explanation. */
public record SignatureCheck(boolean valid, String detail) {
}
