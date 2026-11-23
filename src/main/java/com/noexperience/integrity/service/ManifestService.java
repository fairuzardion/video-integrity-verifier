package com.noexperience.integrity.service;

import com.noexperience.integrity.chain.HashChain;
import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.ManifestSignatureVerifier;
import com.noexperience.integrity.chain.SignatureCheck;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.common.ResourceNotFoundException;
import com.noexperience.integrity.config.IntegrityProperties;
import com.noexperience.integrity.segmenter.SegmentationParams;
import com.noexperience.integrity.storage.JsonCodec;
import com.noexperience.integrity.storage.ManifestStore;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/** Registration with persistence, manifest lookup and JSON import/export. */
@Service
public class ManifestService {

    private final RegistrationService registration;
    private final ManifestStore store;
    private final JsonCodec codec;
    private final ManifestSignatureVerifier signatureVerifier;
    private final IntegrityProperties properties;

    public ManifestService(RegistrationService registration, ManifestStore store, JsonCodec codec,
                           ManifestSignatureVerifier signatureVerifier, IntegrityProperties properties) {
        this.registration = registration;
        this.store = store;
        this.codec = codec;
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
    }

    public SegmentationParams defaultParams() {
        return properties.segmentation().toParams();
    }

    public SignedManifest registerAndStore(Path video, String videoName, Double segmentSecondsOverride) {
        SegmentationParams defaults = defaultParams();
        SegmentationParams params = segmentSecondsOverride == null
                ? defaults
                : new SegmentationParams(segmentSecondsOverride, defaults.sampleFps(), defaults.analysisWidth());
        return store.save(registration.register(video, videoName, params));
    }

    public SignedManifest get(String manifestId) {
        return store.find(manifestId)
                .orElseThrow(() -> new ResourceNotFoundException("No manifest with id " + manifestId));
    }

    public boolean exists(String manifestId) {
        return store.exists(manifestId);
    }

    /** Imports a previously exported manifest after checking its signature and hash chain. */
    public SignedManifest importManifest(String json) {
        SignedManifest signed = codec.parseManifest(json);
        SignatureCheck check = signatureVerifier.verify(signed);
        if (!check.valid()) {
            throw new InvalidInputException("Manifest rejected: " + check.detail());
        }
        Manifest m = signed.manifest();
        if (m.segments().isEmpty() || !HashChain.findBrokenLinks(m.segments()).isEmpty()
                || !HashChain.rootOf(m.segments()).equals(m.chainRootHash())) {
            throw new InvalidInputException("Manifest rejected: its hash chain is inconsistent");
        }
        return store.save(signed);
    }
}
