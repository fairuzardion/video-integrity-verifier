package com.noexperience.integrity.chain;

import com.noexperience.integrity.common.CryptoOperationException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

/** Key generation and PEM (PKCS#8 private / X.509 SubjectPublicKeyInfo public) persistence using Bouncy Castle. */
public final class PemKeys {

    private PemKeys() {
    }

    public static KeyPair generateKeyPair(SignatureAlgorithm algorithm) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm.keyAlgorithm(), BouncyCastleSupport.provider());
            if (algorithm.curve() != null) {
                generator.initialize(new ECGenParameterSpec(algorithm.curve()), new SecureRandom());
            }
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Cannot generate " + algorithm.label() + " key pair: " + e.getMessage(), e);
        }
    }

    public static void writePrivateKey(Path path, PrivateKey key) {
        writePem(path, "PRIVATE KEY", key.getEncoded());
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // non-POSIX file system: rely on the directory's ACLs
        }
    }

    public static void writePublicKey(Path path, PublicKey key) {
        writePem(path, "PUBLIC KEY", key.getEncoded());
    }

    public static PrivateKey readPrivateKey(Path path) {
        try (Reader reader = Files.newBufferedReader(path); PEMParser parser = new PEMParser(reader)) {
            Object object = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleSupport.provider());
            if (object instanceof PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }
            if (object instanceof PEMKeyPair pair) {
                return converter.getKeyPair(pair).getPrivate();
            }
            throw new CryptoOperationException("Unsupported private key PEM in " + path
                    + " (expected an unencrypted PKCS#8 'PRIVATE KEY'; convert with: openssl pkcs8 -topk8 -nocrypt)");
        } catch (IOException e) {
            throw new CryptoOperationException("Cannot read private key " + path + ": " + e.getMessage(), e);
        }
    }

    public static PublicKey readPublicKey(Path path) {
        try (Reader reader = Files.newBufferedReader(path); PEMParser parser = new PEMParser(reader)) {
            Object object = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleSupport.provider());
            if (object instanceof SubjectPublicKeyInfo info) {
                return converter.getPublicKey(info);
            }
            throw new CryptoOperationException("Unsupported public key PEM in " + path + " (expected 'PUBLIC KEY')");
        } catch (IOException e) {
            throw new CryptoOperationException("Cannot read public key " + path + ": " + e.getMessage(), e);
        }
    }

    private static void writePem(Path path, String type, byte[] der) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path); PemWriter pem = new PemWriter(writer)) {
                pem.writeObject(new PemObject(type, der));
            }
        } catch (IOException e) {
            throw new CryptoOperationException("Cannot write key file " + path + ": " + e.getMessage(), e);
        }
    }
}
