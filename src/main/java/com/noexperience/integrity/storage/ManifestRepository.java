package com.noexperience.integrity.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManifestRepository extends JpaRepository<ManifestEntity, String> {

    List<ManifestEntity> findByVideoSha256OrderByCreatedAtDesc(String videoSha256);
}
