package com.noexperience.integrity.chain;

import java.security.Provider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Holds one Bouncy Castle provider instance that is passed explicitly to JCA factories (no global registration). */
public final class BouncyCastleSupport {

    private static final Provider PROVIDER = new BouncyCastleProvider();

    private BouncyCastleSupport() {
    }

    public static Provider provider() {
        return PROVIDER;
    }
}
