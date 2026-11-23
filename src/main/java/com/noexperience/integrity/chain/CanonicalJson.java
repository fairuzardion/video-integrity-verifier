package com.noexperience.integrity.chain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.noexperience.integrity.common.IntegrityException;
import java.nio.charset.StandardCharsets;

/**
 * Deterministic JSON encoding used as the byte string that gets signed: compact (no whitespace), object keys sorted
 * alphabetically, UTF-8. The signature is always computed over a re-serialization of the parsed model, never over the
 * received text, so pretty-printing or key order of an exported manifest file cannot affect verification.
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    private CanonicalJson() {
    }

    public static byte[] toBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IntegrityException("Cannot canonicalize JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static String toString(Object value) {
        return new String(toBytes(value), StandardCharsets.UTF_8);
    }
}
