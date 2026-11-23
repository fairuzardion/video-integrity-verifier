package com.noexperience.integrity.storage;

import com.noexperience.integrity.chain.SignedManifest;
import java.util.Optional;

/** Storage abstraction for signed manifests (JPA today, anything else tomorrow). */
public interface ManifestStore {

    SignedManifest save(SignedManifest manifest);

    Optional<SignedManifest> find(String manifestId);

    boolean exists(String manifestId);
}
