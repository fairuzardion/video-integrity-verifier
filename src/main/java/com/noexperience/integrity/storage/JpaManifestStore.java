package com.noexperience.integrity.storage;

import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.SignedManifest;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaManifestStore implements ManifestStore {

    private final ManifestRepository repository;
    private final JsonCodec codec;

    public JpaManifestStore(ManifestRepository repository, JsonCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    @Transactional
    public SignedManifest save(SignedManifest signed) {
        Manifest m = signed.manifest();
        repository.save(new ManifestEntity(m.manifestId(), m.videoName(), m.videoSha256(), m.keyId(),
                Instant.parse(m.createdAt()), codec.toJson(signed)));
        return signed;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignedManifest> find(String manifestId) {
        return repository.findById(manifestId).map(e -> codec.parseManifest(e.getManifestJson()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String manifestId) {
        return repository.existsById(manifestId);
    }
}
