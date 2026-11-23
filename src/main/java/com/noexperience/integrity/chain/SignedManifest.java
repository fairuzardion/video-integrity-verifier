package com.noexperience.integrity.chain;

import java.util.Objects;

/**
 * A manifest together with its detached signature.
 *
 * @param signature Base64 signature over the canonical JSON bytes of {@code manifest} ({@link CanonicalJson})
 */
public record SignedManifest(Manifest manifest, String signature) {

    public SignedManifest {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(signature, "signature");
    }
}
