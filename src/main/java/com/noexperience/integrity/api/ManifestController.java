package com.noexperience.integrity.api;

import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.service.ManifestService;
import com.noexperience.integrity.service.VerificationService;
import com.noexperience.integrity.storage.ReportSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manifests")
public class ManifestController {

    private final ManifestService manifestService;
    private final VerificationService verificationService;

    public ManifestController(ManifestService manifestService, VerificationService verificationService) {
        this.manifestService = manifestService;
        this.verificationService = verificationService;
    }

    /** Exports the signed manifest as JSON (save it and use it with the CLI or POST /videos/verify). */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SignedManifest get(@PathVariable("id") String id) {
        return manifestService.get(id);
    }

    /** Imports an exported signed manifest; rejected unless signature and chain are valid for the trusted key. */
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImportResponse> importManifest(@RequestBody String json) {
        Manifest m = manifestService.importManifest(json).manifest();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ImportResponse(m.manifestId(), m.videoName(), m.segments().size()));
    }

    /** Lists the verification reports produced against this manifest, newest first. */
    @GetMapping(value = "/{id}/reports", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ReportSummary> reports(@PathVariable("id") String id) {
        return verificationService.listReports(id);
    }
}
