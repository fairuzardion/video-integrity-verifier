package com.noexperience.integrity.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.common.IntegrityException;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.verifier.VerificationReport;
import org.springframework.stereotype.Component;

/**
 * JSON export/import of manifests and reports. Parsing is strict: unknown properties are rejected so that nothing
 * unsigned can ride along inside an imported manifest.
 */
@Component
public class JsonCodec {

    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .build();

    public String toJson(SignedManifest manifest) {
        return write(manifest);
    }

    public String toJson(VerificationReport report) {
        return write(report);
    }

    public SignedManifest parseManifest(String json) {
        try {
            return mapper.readValue(json, SignedManifest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidInputException("Malformed manifest JSON: " + e.getOriginalMessage(), e);
        }
    }

    public VerificationReport parseReport(String json) {
        try {
            return mapper.readValue(json, VerificationReport.class);
        } catch (JsonProcessingException e) {
            throw new IntegrityException("Stored report JSON is corrupt: " + e.getOriginalMessage(), e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IntegrityException("Cannot serialize to JSON: " + e.getOriginalMessage(), e);
        }
    }
}
