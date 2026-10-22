package cn.maple.feign.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the environment templates shipped in {@code config/} bind to the prefix used
 * by {@code GXCircuitBreakerProperties}.
 */
class GXFeignConfigTemplateTest {
    private static final List<String> ENVIRONMENTS = List.of("dev", "test", "pre", "prod");

    private static final String CIRCUIT_BREAKER_ANCHOR = "      circuit-breaker:";

    private static final String KEY_INDENT = "        ";

    @Test
    void templatesUseFrameworkFeignPrefixAndDisableRetries() throws IOException {
        for (String environment : ENVIRONMENTS) {
            Path template = resolveTemplate(environment);
            assertThat(template).as("template for environment %s", environment).exists();

            List<String> lines = Files.readAllLines(template, StandardCharsets.UTF_8);

            assertThat(lines)
                    .as("environment %s must nest feign under framework", environment)
                    .contains("  framework:", "    feign:", "      circuit-breaker:")
                    .contains("        max-retries: 0")
                    .noneMatch(line -> line.startsWith("  feign:"))
                    .noneMatch(line -> line.trim().equals("max-retries: 3"));
        }
    }

    @Test
    void templatesDeclareEveryCircuitBreakerKeyWithTheCodeDefault() throws IOException {
        Map<String, String> expectedKeys = Map.of(
                "enabled", "true",
                "max-retries", "0",
                "backoff", "100ms",
                "backoff-multiplier", "2.0",
                "open-timeout", "20s",
                "reset-timeout", "5s"
        );

        for (String environment : ENVIRONMENTS) {
            Path template = resolveTemplate(environment);
            assertThat(template).as("template for environment %s", environment).exists();
            List<String> lines = Files.readAllLines(template, StandardCharsets.UTF_8);

            Map<String, String> circuitBreakerBlock = circuitBreakerBlock(lines);

            assertThat(circuitBreakerBlock)
                    .as("environment %s must declare exactly the six circuit breaker keys", environment)
                    .hasSize(6)
                    .containsExactlyInAnyOrderEntriesOf(expectedKeys);
        }
    }

    private Map<String, String> circuitBreakerBlock(List<String> lines) {
        int start = lines.indexOf(CIRCUIT_BREAKER_ANCHOR);
        assertThat(start).as("circuit breaker block anchor").isGreaterThanOrEqualTo(0);

        Map<String, String> block = new LinkedHashMap<>();
        for (int index = start + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.startsWith(KEY_INDENT)) {
                break;
            }
            String entry = line.trim();
            int separator = entry.indexOf(':');
            block.put(entry.substring(0, separator), entry.substring(separator + 1).trim());
        }
        return block;
    }

    @Test
    void templatesDoNotReferenceTheLegacyMapleFeignPrefix() throws IOException {
        for (String environment : ENVIRONMENTS) {
            Path template = resolveTemplate(environment);
            assertThat(template).as("template for environment %s", environment).exists();

            String content = Files.readString(template, StandardCharsets.UTF_8);

            assertThat(content)
                    .as("environment %s must not use the legacy maple.feign.* prefix", environment)
                    .doesNotContain("maple.feign.");
        }
    }

    private Path resolveTemplate(String environment) {
        Path moduleRelative = Path.of("..", "config", environment, "app-environment.yml");
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("config", environment, "app-environment.yml");
    }
}
