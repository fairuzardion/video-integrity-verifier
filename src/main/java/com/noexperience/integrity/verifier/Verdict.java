package com.noexperience.integrity.verifier;

/** Overall result of a verification. */
public enum Verdict {
    /** Content matches the signed reference (byte-identical, or identical decoded content). */
    AUTHENTIC,
    /** Pixel-level differences only; every segment is still perceptually similar (re-encoding, mild processing). */
    MODIFIED_OR_REENCODED,
    /** Cut/trim, splice/insertion, timestamp shift or visual alteration detected. */
    TAMPERED,
    /** The reference manifest is not trustworthy: bad signature, wrong key or inconsistent hash chain. */
    INVALID_SIGNATURE
}
