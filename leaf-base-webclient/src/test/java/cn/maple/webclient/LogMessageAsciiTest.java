package cn.maple.webclient;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LogMessageAsciiTest {
    @Test
    void logStatementsUseAsciiText() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            List<String> nonAsciiLogLines = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(this::readLines)
                    .filter(line -> line.contains("LOGGER.") || line.contains("log."))
                    .filter(line -> line.chars().anyMatch(ch -> ch > 127))
                    .toList();

            assertThat(nonAsciiLogLines).isEmpty();
        }
    }

    private Stream<String> readLines(Path path) {
        try {
            return Files.readAllLines(path).stream();
        } catch (IOException e) {
            throw new IllegalStateException("Read source file failed: " + path, e);
        }
    }
}
