package com.noexperience.integrity.api;

import com.noexperience.integrity.chain.SignedManifest;

public record RegisterResponse(
        String manifestId,
        String videoName,
        String videoSha256,
        double durationSec,
        int segmentCount,
        String signatureAlgorithm,
        String keyId,
        SignedManifest signedManifest) {
}
