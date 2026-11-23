package com.noexperience.integrity.api;

import com.noexperience.integrity.service.VerificationService;
import com.noexperience.integrity.verifier.VerificationReport;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final VerificationService verificationService;

    public ReportController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public VerificationReport get(@PathVariable("id") String id) {
        return verificationService.getReport(id);
    }
}
