package cn.maple.dubbo.nacos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiSourceGuardTest {
    @Test
    void mainJavaSourcesStayAsciiOnly() throws IOException {
        Path sourceRoot = Path.of("src/main/java");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> nonAsciiFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsNonAscii)
                    .toList();

            assertTrue(nonAsciiFiles.isEmpty(), () -> "Non-ASCII source files: " + nonAsciiFiles);
        }
    }

    private boolean containsNonAscii(Path path) {
        try {
            String content = Files.readString(path);
            return content.chars().anyMatch(character -> character > 0x7F);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
