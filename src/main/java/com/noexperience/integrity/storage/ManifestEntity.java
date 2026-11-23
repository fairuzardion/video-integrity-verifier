package com.noexperience.integrity.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Persisted signed manifest: queryable columns plus the full signed JSON document. */
@Entity
@Table(name = "manifests")
public class ManifestEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 512)
    private String videoName;

    @Column(nullable = false, length = 64)
    private String videoSha256;

    @Column(nullable = false, length = 64)
    private String keyId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "text")
    private String manifestJson;

    protected ManifestEntity() {
        // required by JPA
    }

    public ManifestEntity(String id, String videoName, String videoSha256, String keyId, Instant createdAt,
                          String manifestJson) {
        this.id = id;
        this.videoName = videoName;
        this.videoSha256 = videoSha256;
        this.keyId = keyId;
        this.createdAt = createdAt;
        this.manifestJson = manifestJson;
    }

    public String getId() {
        return id;
    }

    public String getVideoName() {
        return videoName;
    }

    public String getVideoSha256() {
        return videoSha256;
    }

    public String getKeyId() {
        return keyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getManifestJson() {
        return manifestJson;
    }
}
