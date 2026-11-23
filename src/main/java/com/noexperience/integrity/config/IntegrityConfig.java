package com.noexperience.integrity.config;

import com.noexperience.integrity.chain.ConfiguredKeyProvider;
import com.noexperience.integrity.chain.KeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrityConfig {

    /** Lazily loads (or generates) key material on first use, so verify-only deployments need no private key. */
    @Bean
    public KeyProvider keyProvider(IntegrityProperties properties) {
        return new ConfiguredKeyProvider(properties.keys());
    }
}
