package cn.maple.eureka.client.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the parts of the contract that are not exercised by runtime behaviour: the configuration
 * metadata, the absence of shipped registration/Feign defaults, and the documentation that describes
 * the switch. These assertions back the audit findings F-06, F-08, F-10 and F-13.
 */
class GXEurekaClientContractTest {
    @Test
    void metadataDeclaresOnlyTheModuleSwitchWithDefaultTrue() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(inputStream).isNotNull();
            String metadata = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(metadata).contains("maple.framework.enable.eureka-client");
            assertThat(metadata).contains("\"defaultValue\": true");
            assertThat(metadata).doesNotContain("\"name\": \"eureka.");
            assertThat(metadata).doesNotContain("\"name\": \"spring.cloud.openfeign.");
        }
    }

    @Test
    void producedClassesContainNoRegistrationDefaultsOrFeignKeys() throws IOException {
        Path classesDirectory = Path.of("target", "classes");
        assertThat(classesDirectory).as("run tests through Maven from the module directory").isDirectory();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classesDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                if (content.contains("eureka700")) {
                    violations.add(classesDirectory.relativize(file) + " -> eureka700");
                }
                if (content.contains("spring.cloud.openfeign.circuitbreaker")) {
                    violations.add(classesDirectory.relativize(file) + " -> spring.cloud.openfeign.circuitbreaker");
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    /**
     * Spring Boot's autoconfigure metadata records {@code AutoConfigureAfter} and
     * {@code ConditionalOnClass} but never {@code ConditionalOnProperty}; the switch itself is guarded
     * by the annotation below plus the additional configuration metadata.
     */
    @Test
    void autoconfigureMetadataKeepsDocumentedKeys() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring-autoconfigure-metadata.properties")) {
            assertThat(inputStream).isNotNull();
            String metadata = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(metadata).contains("GXEurekaClientConfig.AutoConfigureAfter=");
            assertThat(metadata).contains("GXEurekaClientConfig.ConditionalOnClass=");
            assertThat(metadata).doesNotContain("eureka700");
        }
    }

    @Test
    void switchConditionIsDeclaredOnTheConfigurationClass() {
        ConditionalOnProperty condition = GXEurekaClientConfig.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("maple.framework.enable");
        assertThat(condition.name()).containsExactly("eureka-client");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    void documentationDescribesTheSwitch() throws IOException {
        String agents = Files.readString(Path.of("AGENTS.md"), StandardCharsets.UTF_8);
        assertThat(agents).contains("maple.framework.enable.eureka-client");

        String manual = Files.readString(Path.of("..", "docs", "leaf-base-eureka-client开发手册.md"),
                StandardCharsets.UTF_8);
        assertThat(manual).contains("maple.framework.enable.eureka-client");
        assertThat(manual).doesNotContain("eureka7001.com");
    }
}
