package cn.maple.extension;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe repository for extension point implementations.
 */
@Component
public class GXExtensionRepository {
    private final ConcurrentMap<GXExtensionCoordinate, GXExtensionPoint> extensionRepo = new ConcurrentHashMap<>();

    public Map<GXExtensionCoordinate, GXExtensionPoint> getExtensionRepo() {
        return Collections.unmodifiableMap(extensionRepo);
    }

    public Optional<GXExtensionPoint> findExtension(GXExtensionCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "Extension coordinate cannot be null");
        return Optional.ofNullable(extensionRepo.get(coordinate));
    }

    public Optional<GXExtensionPoint> findExtension(String extensionPoint, String bizScenario) {
        return findExtension(GXExtensionCoordinate.valueOf(extensionPoint, bizScenario));
    }

    public void registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension) {
        Objects.requireNonNull(coordinate, "Extension coordinate cannot be null");
        Objects.requireNonNull(extension, "Extension implementation cannot be null");
        extensionRepo.put(coordinate, extension);
    }

    public Optional<GXExtensionPoint> registerExtensionIfAbsent(GXExtensionCoordinate coordinate, GXExtensionPoint extension) {
        Objects.requireNonNull(coordinate, "Extension coordinate cannot be null");
        Objects.requireNonNull(extension, "Extension implementation cannot be null");
        return Optional.ofNullable(extensionRepo.putIfAbsent(coordinate, extension));
    }

    public int size() {
        return extensionRepo.size();
    }
}
