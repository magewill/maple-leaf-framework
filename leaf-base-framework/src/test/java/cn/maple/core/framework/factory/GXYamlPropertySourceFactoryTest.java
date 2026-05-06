package cn.maple.core.framework.factory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXYamlPropertySourceFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldLoadYamlWithExplicitPropertySourceName() throws Exception {
        Path yaml = tempDir.resolve("demo.yml");
        Files.writeString(yaml, "demo:\n  name: alpha\n", StandardCharsets.UTF_8);

        GXYamlPropertySourceFactory factory = new GXYamlPropertySourceFactory();
        PropertySource<?> propertySource = factory.createPropertySource(
                "customYamlSource",
                new EncodedResource(new FileSystemResource(yaml))
        );

        assertEquals("customYamlSource", propertySource.getName());
        assertEquals("alpha", propertySource.getProperty("demo.name"));
    }
}
