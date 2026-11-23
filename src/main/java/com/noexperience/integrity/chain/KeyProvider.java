package com.noexperience.integrity.chain;

import com.noexperience.integrity.fingerprint.Sha256;
import java.security.PrivateKey;
import java.security.PublicKey;

/** Supplies the signing/verification keys. */
public interface KeyProvider {

    SignatureAlgorithm algorithm();

    /** @throws com.noexperience.integrity.common.CryptoOperationException when only a public key is available */
    PrivateKey privateKey();

    PublicKey publicKey();

    /** Short fingerprint of the public key (first 16 hex chars of SHA-256 over its X.509 encoding). */
    default String keyId() {
        return Sha256.hashHex(publicKey().getEncoded()).substring(0, 16);
    }
}
