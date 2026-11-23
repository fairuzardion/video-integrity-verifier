package com.noexperience.integrity.support;

import com.noexperience.integrity.chain.KeyProvider;
import com.noexperience.integrity.chain.PemKeys;
import com.noexperience.integrity.chain.SignatureAlgorithm;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public final class InMemoryKeyProvider implements KeyProvider {

    private final SignatureAlgorithm algorithm;
    private final KeyPair pair;

    public InMemoryKeyProvider(SignatureAlgorithm algorithm) {
        this(algorithm, PemKeys.generateKeyPair(algorithm));
    }

    public InMemoryKeyProvider(SignatureAlgorithm algorithm, KeyPair pair) {
        this.algorithm = algorithm;
        this.pair = pair;
    }

    @Override
    public SignatureAlgorithm algorithm() {
        return algorithm;
    }

    @Override
    public PrivateKey privateKey() {
        return pair.getPrivate();
    }

    @Override
    public PublicKey publicKey() {
        return pair.getPublic();
    }
}
