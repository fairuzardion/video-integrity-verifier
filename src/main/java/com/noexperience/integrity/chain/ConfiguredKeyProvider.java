package com.noexperience.integrity.chain;

import com.noexperience.integrity.common.CryptoOperationException;
import com.noexperience.integrity.config.IntegrityProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads keys lazily, in this order:
 * <ol>
 *   <li>PKCS#12 keystore ({@code integrity.keys.keystore-path}, alias and password from configuration)</li>
 *   <li>PEM files (private + public), or the public PEM alone for verify-only deployments</li>
 *   <li>a freshly generated key pair written to the configured PEM paths, if {@code auto-generate} is on</li>
 * </ol>
 */
public final class ConfiguredKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(ConfiguredKeyProvider.class);

    private record Loaded(PrivateKey privateKey, PublicKey publicKey) {
    }

    private final IntegrityProperties.Keys config;
    private volatile Loaded loaded;

    public ConfiguredKeyProvider(IntegrityProperties.Keys config) {
        this.config = config;
    }

    @Override
    public SignatureAlgorithm algorithm() {
        return config.algorithm();
    }

    @Override
    public PrivateKey privateKey() {
        PrivateKey key = loaded().privateKey();
        if (key == null) {
            throw new CryptoOperationException("No private key is available (verify-only configuration). "
                    + "Configure integrity.keys.private-key-path or a keystore to sign manifests.");
        }
        return key;
    }

    @Override
    public PublicKey publicKey() {
        return loaded().publicKey();
    }

    private Loaded loaded() {
        Loaded result = loaded;
        if (result == null) {
            synchronized (this) {
                result = loaded;
                if (result == null) {
                    result = load();
                    loaded = result;
                }
            }
        }
        return result;
    }

    private Loaded load() {
        Loaded result;
        if (hasText(config.keystorePath())) {
            result = loadFromKeystore();
        } else {
            result = loadFromPem();
        }
        if (result.privateKey() != null) {
            requireAlgorithm(result.privateKey());
        }
        requireAlgorithm(result.publicKey());
        log.info("Using {} key {} ({})", config.algorithm().label(),
                com.noexperience.integrity.fingerprint.Sha256.hashHex(result.publicKey().getEncoded()).substring(0, 16),
                result.privateKey() != null ? "signing + verification" : "verification only");
        return result;
    }

    private Loaded loadFromPem() {
        Path privatePath = Path.of(config.privateKeyPath());
        Path publicPath = Path.of(config.publicKeyPath());
        boolean hasPrivate = Files.isRegularFile(privatePath);
        boolean hasPublic = Files.isRegularFile(publicPath);
        if (hasPrivate && hasPublic) {
            return new Loaded(PemKeys.readPrivateKey(privatePath), PemKeys.readPublicKey(publicPath));
        }
        if (hasPrivate) {
            throw new CryptoOperationException("Private key found at " + privatePath + " but the public key "
                    + publicPath + " is missing. Derive it with: openssl pkey -in " + privatePath
                    + " -pubout -out " + publicPath);
        }
        if (hasPublic) {
            return new Loaded(null, PemKeys.readPublicKey(publicPath));
        }
        if (config.autoGenerate()) {
            KeyPair pair = PemKeys.generateKeyPair(config.algorithm());
            PemKeys.writePrivateKey(privatePath, pair.getPrivate());
            PemKeys.writePublicKey(publicPath, pair.getPublic());
            log.warn("No keys found: generated a new {} key pair ({} / {}). Back up the private key and distribute "
                    + "the public key to verifiers.", config.algorithm().label(), privatePath, publicPath);
            return new Loaded(pair.getPrivate(), pair.getPublic());
        }
        throw new CryptoOperationException("No key material found at " + privatePath + " / " + publicPath
                + " and integrity.keys.auto-generate is false. Run the 'keygen' CLI command or provide keys.");
    }

    private Loaded loadFromKeystore() {
        Path path = Path.of(config.keystorePath());
        char[] password = config.keystorePassword() == null ? new char[0] : config.keystorePassword().toCharArray();
        try (InputStream in = Files.newInputStream(path)) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(in, password);
            Key key = store.getKey(config.keyAlias(), password);
            Certificate certificate = store.getCertificate(config.keyAlias());
            if (!(key instanceof PrivateKey privateKey) || certificate == null) {
                throw new CryptoOperationException("Keystore " + path + " has no private key + certificate under alias '"
                        + config.keyAlias() + "'");
            }
            return new Loaded(privateKey, certificate.getPublicKey());
        } catch (IOException | GeneralSecurityException e) {
            throw new CryptoOperationException("Cannot load keystore " + path + ": " + e.getMessage(), e);
        }
    }

    private void requireAlgorithm(Key key) {
        if (!config.algorithm().acceptsKeyAlgorithm(key.getAlgorithm())) {
            throw new CryptoOperationException("Configured algorithm " + config.algorithm().label()
                    + " does not match the loaded key type '" + key.getAlgorithm()
                    + "' (check integrity.keys.algorithm)");
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
