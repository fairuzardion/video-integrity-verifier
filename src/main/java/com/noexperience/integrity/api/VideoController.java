package com.noexperience.integrity.api;

import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.service.ManifestService;
import com.noexperience.integrity.service.VerificationService;
import com.noexperience.integrity.storage.JsonCodec;
import com.noexperience.integrity.verifier.VerificationReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/videos")
public class VideoController {

    private static final long MAX_MANIFEST_BYTES = 32L * 1024 * 1024;

    private final ManifestService manifestService;
    private final VerificationService verificationService;
    private final JsonCodec codec;

    public VideoController(ManifestService manifestService, VerificationService verificationService, JsonCodec codec) {
        this.manifestService = manifestService;
        this.verificationService = verificationService;
        this.codec = codec;
    }

    /** Segments, fingerprints, chains and signs the uploaded video, stores the manifest and returns it. */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisterResponse> register(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "segmentSeconds", required = false) Double segmentSeconds) {
        try (TempUpload upload = TempUpload.from(file)) {
            SignedManifest signed = manifestService.registerAndStore(upload.path(), upload.displayName(), segmentSeconds);
            Manifest m = signed.manifest();
            RegisterResponse body = new RegisterResponse(m.manifestId(), m.videoName(), m.videoSha256(),
                    m.durationSec(), m.segments().size(), m.signatureAlgorithm(), m.keyId(), signed);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        }
    }

    /**
     * Verifies the uploaded video against a stored manifest ({@code manifestId}) or against a manifest JSON file
     * supplied in the {@code manifest} part. Exactly one of the two must be provided.
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public VerificationReport verify(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "manifestId", required = false) String manifestId,
            @RequestPart(name = "manifest", required = false) MultipartFile manifestFile) {
        boolean hasId = manifestId != null && !manifestId.isBlank();
        boolean hasFile = manifestFile != null && !manifestFile.isEmpty();
        if (hasId == hasFile) {
            throw new InvalidInputException("Provide exactly one of 'manifestId' or a 'manifest' file part");
        }
        try (TempUpload upload = TempUpload.from(file)) {
            if (hasId) {
                return verificationService.verifyAgainstStored(upload.path(), upload.displayName(), manifestId.trim());
            }
            if (manifestFile.getSize() > MAX_MANIFEST_BYTES) {
                throw new InvalidInputException("Manifest file is larger than " + MAX_MANIFEST_BYTES + " bytes");
            }
            String json;
            try {
                json = new String(manifestFile.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new InvalidInputException("Cannot read the manifest part: " + e.getMessage(), e);
            }
            return verificationService.verifyAgainst(upload.path(), upload.displayName(), codec.parseManifest(json));
        }
    }
}
