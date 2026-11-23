package com.noexperience.integrity.chain;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Verifies a manifest signature against the <em>configured trusted public key</em>. No key is ever taken from the
 * manifest itself: it only carries the algorithm label and key id, which must match the trusted key.
 */
@Component
public class ManifestSignatureVerifier {

    private final KeyProvider keys;

    public ManifestSignatureVerifier(KeyProvider keys) {
        this.keys = keys;
    }

    public SignatureCheck verify(SignedManifest signed) {
        Manifest manifest = signed.manifest();
        SignatureAlgorithm algorithm = keys.algorithm();
        if (!algorithm.label().equals(manifest.signatureAlgorithm())) {
            return new SignatureCheck(false, "manifest declares algorithm '" + manifest.signatureAlgorithm()
                    + "' but the trusted key uses '" + algorithm.label() + "'");
        }
        String trustedKeyId = keys.keyId();
        if (!trustedKeyId.equals(manifest.keyId())) {
            return new SignatureCheck(false, "manifest was signed by key " + manifest.keyId()
                    + " which is not the trusted key " + trustedKeyId);
        }
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signed.signature());
        } catch (IllegalArgumentException e) {
            return new SignatureCheck(false, "signature is not valid Base64");
        }
        try {
            Signature verifier = Signature.getInstance(algorithm.jcaSignatureName(), BouncyCastleSupport.provider());
            verifier.initVerify(keys.publicKey());
            verifier.update(CanonicalJson.toBytes(manifest));
            if (verifier.verify(signatureBytes)) {
                return new SignatureCheck(true, "signature valid (" + algorithm.label() + ", key " + trustedKeyId + ")");
            }
            return new SignatureCheck(false, "signature does not match the manifest content (manifest altered or forged)");
        } catch (GeneralSecurityException e) {
            return new SignatureCheck(false, "signature verification error: " + e.getMessage());
        }
    }
}
