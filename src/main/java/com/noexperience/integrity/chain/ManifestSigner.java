package com.noexperience.integrity.chain;

import com.noexperience.integrity.common.CryptoOperationException;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Signs the canonical JSON of a {@link Manifest} with the configured private key (Ed25519 or ECDSA P-256). */
@Component
public class ManifestSigner {

    private final KeyProvider keys;

    public ManifestSigner(KeyProvider keys) {
        this.keys = keys;
    }

    public SignatureAlgorithm algorithm() {
        return keys.algorithm();
    }

    public String keyId() {
        return keys.keyId();
    }

    public SignedManifest sign(Manifest manifest) {
        try {
            Signature signature = Signature.getInstance(keys.algorithm().jcaSignatureName(), BouncyCastleSupport.provider());
            signature.initSign(keys.privateKey());
            signature.update(CanonicalJson.toBytes(manifest));
            return new SignedManifest(manifest, Base64.getEncoder().encodeToString(signature.sign()));
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Signing failed: " + e.getMessage(), e);
        }
    }
}
