package com.noexperience.integrity.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noexperience.integrity.common.CryptoOperationException;
import com.noexperience.integrity.config.IntegrityProperties;
import com.noexperience.integrity.storage.JsonCodec;
import com.noexperience.integrity.support.InMemoryKeyProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ManifestSignerTest {

    static Manifest manifest(KeyProvider keys) {
        var chain = HashChain.build(HashChainTest.segments(3));
        return new Manifest("id-1", 1, "clip.mp4", "f".repeat(64), 15.0, 5.0, 2.0, 320,
                "2026-01-01T00:00:00Z", keys.algorithm().label(), keys.keyId(), chain, HashChain.rootOf(chain));
    }

    @ParameterizedTest
    @EnumSource(SignatureAlgorithm.class)
    void signsAndVerifies(SignatureAlgorithm alg) {
        InMemoryKeyProvider keys = new InMemoryKeyProvider(alg);
        SignedManifest signed = new ManifestSigner(keys).sign(manifest(keys));
        assertTrue(new ManifestSignatureVerifier(keys).verify(signed).valid());
    }

    @ParameterizedTest
    @EnumSource(SignatureAlgorithm.class)
    void rejectsTamperedManifest(SignatureAlgorithm alg) {
        InMemoryKeyProvider keys = new InMemoryKeyProvider(alg);
        SignedManifest signed = new ManifestSigner(keys).sign(manifest(keys));
        Manifest m = signed.manifest();
        Manifest forged = new Manifest(m.manifestId(), m.formatVersion(), "other.mp4", m.videoSha256(),
                m.durationSec(), m.segmentDurationSec(), m.sampleFps(), m.analysisWidth(), m.createdAt(),
                m.signatureAlgorithm(), m.keyId(), m.segments(), m.chainRootHash());
        assertFalse(new ManifestSignatureVerifier(keys).verify(new SignedManifest(forged, signed.signature())).valid());
    }

    @Test
    void rejectsSignatureFromAnotherKeyAndGarbage() {
        InMemoryKeyProvider signerKeys = new InMemoryKeyProvider(SignatureAlgorithm.ED25519);
        InMemoryKeyProvider otherKeys = new InMemoryKeyProvider(SignatureAlgorithm.ED25519);
        SignedManifest signed = new ManifestSigner(signerKeys).sign(manifest(signerKeys));
        assertFalse(new ManifestSignatureVerifier(otherKeys).verify(signed).valid());
        assertFalse(new ManifestSignatureVerifier(signerKeys)
                .verify(new SignedManifest(signed.manifest(), "###")).valid());
        String zeros = Base64.getEncoder().encodeToString(new byte[64]);
        assertFalse(new ManifestSignatureVerifier(signerKeys)
                .verify(new SignedManifest(signed.manifest(), zeros)).valid());
    }

    @Test
    void signatureSurvivesJsonExportImport() {
        InMemoryKeyProvider keys = new InMemoryKeyProvider(SignatureAlgorithm.ECDSA_P256);
        SignedManifest signed = new ManifestSigner(keys).sign(manifest(keys));
        JsonCodec codec = new JsonCodec();
        SignedManifest imported = codec.parseManifest(codec.toJson(signed));
        assertEquals(signed, imported);
        assertTrue(new ManifestSignatureVerifier(keys).verify(imported).valid());
    }

    @Test
    void pemRoundTripAndAutoGeneration(@TempDir Path dir) {
        for (SignatureAlgorithm alg : SignatureAlgorithm.values()) {
            Path priv = dir.resolve(alg + "-priv.pem");
            Path pub = dir.resolve(alg + "-pub.pem");
            IntegrityProperties.Keys cfg = new IntegrityProperties.Keys(alg, priv.toString(), pub.toString(),
                    null, null, "integrity", true);

            ConfiguredKeyProvider first = new ConfiguredKeyProvider(cfg);
            String keyId = first.keyId();
            assertTrue(Files.exists(priv) && Files.exists(pub));
            SignedManifest signed = new ManifestSigner(first).sign(manifest(first));

            ConfiguredKeyProvider second = new ConfiguredKeyProvider(cfg);
            assertEquals(keyId, second.keyId());
            assertTrue(new ManifestSignatureVerifier(second).verify(signed).valid());
        }
    }

    @Test
    void verifyOnlyProviderCannotSign(@TempDir Path dir) {
        KeyPair pair = PemKeys.generateKeyPair(SignatureAlgorithm.ED25519);
        Path pub = dir.resolve("pub.pem");
        PemKeys.writePublicKey(pub, pair.getPublic());
        ConfiguredKeyProvider provider = new ConfiguredKeyProvider(new IntegrityProperties.Keys(
                SignatureAlgorithm.ED25519, dir.resolve("absent.pem").toString(), pub.toString(), null, null, "x", false));
        assertNotNull(provider.publicKey());
        assertThrows(CryptoOperationException.class, provider::privateKey);
    }

    @Test
    void missingKeysWithoutAutoGenerateFail(@TempDir Path dir) {
        ConfiguredKeyProvider provider = new ConfiguredKeyProvider(new IntegrityProperties.Keys(
                SignatureAlgorithm.ED25519, dir.resolve("a.pem").toString(), dir.resolve("b.pem").toString(),
                null, null, "x", false));
        assertThrows(CryptoOperationException.class, provider::publicKey);
    }
}
