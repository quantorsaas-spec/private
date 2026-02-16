package com.quantor.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigFilesGuardTest {

    @Test
    void gcpConfigMustSupportQuantorDbPasswordFallback() throws Exception {
        var path = "src/main/resources/application-gcp.yml";
        var content = java.nio.file.Files.readString(java.nio.file.Path.of(path), StandardCharsets.UTF_8);

        // Must support QUANTOR_DB_PASSWORD (new) while keeping backward compatibility with QUANTOR_DB_PASS (old).
        assertThat(content)
                .contains("password: ${QUANTOR_DB_PASSWORD:${QUANTOR_DB_PASS:}}");
    }
}
