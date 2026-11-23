package com.noexperience.integrity.chain;

import com.noexperience.integrity.common.InvalidInputException;
import java.util.Locale;

/** Supported digital-signature schemes (implemented by Bouncy Castle). */
public enum SignatureAlgorithm {

    ED25519("Ed25519", "Ed25519", "Ed25519", null),
    ECDSA_P256("ECDSA-P256-SHA256", "SHA256withECDSA", "EC", "secp256r1");

    private final String label;
    private final String jcaSignatureName;
    private final String keyAlgorithm;
    private final String curve;

    SignatureAlgorithm(String label, String jcaSignatureName, String keyAlgorithm, String curve) {
        this.label = label;
        this.jcaSignatureName = jcaSignatureName;
        this.keyAlgorithm = keyAlgorithm;
        this.curve = curve;
    }

    /** Stable identifier stored inside manifests. */
    public String label() {
        return label;
    }

    public String jcaSignatureName() {
        return jcaSignatureName;
    }

    public String keyAlgorithm() {
        return keyAlgorithm;
    }

    /** Named curve for EC keys, {@code null} otherwise. */
    public String curve() {
        return curve;
    }

    /** Whether a JCA key algorithm name (as reported by {@code Key.getAlgorithm()}) belongs to this scheme. */
    public boolean acceptsKeyAlgorithm(String name) {
        if (name == null) {
            return false;
        }
        return switch (this) {
            case ED25519 -> name.equalsIgnoreCase("Ed25519") || name.equalsIgnoreCase("EdDSA");
            case ECDSA_P256 -> name.equalsIgnoreCase("EC") || name.equalsIgnoreCase("ECDSA");
        };
    }

    public static SignatureAlgorithm parse(String text) {
        String n = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (n) {
            case "ed25519" -> ED25519;
            case "ecdsa", "ecdsa-p256", "ecdsa-p256-sha256", "es256" -> ECDSA_P256;
            default -> throw new InvalidInputException("Unknown signature algorithm '" + text
                    + "'. Supported: ed25519, ecdsa-p256");
        };
    }
}
