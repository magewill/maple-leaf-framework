package cn.maple.extension;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

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
        return Map.copyOf(extensionRepo);
    }

    @NullMarked
    public Optional<GXExtensionPoint> findExtension(GXExtensionCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "Extension coordinate cannot be null");
        return Optional.ofNullable(extensionRepo.get(coordinate));
    }

    /**
     * Looks up an implementation without wrapping the result, for the hot resolution path.
     *
     * @param extensionPoint extension point interface name
     * @param bizScenario    scenario identity
     * @return matching implementation, or {@code null} when the coordinate is not registered
     */
    @Nullable
    GXExtensionPoint findExtensionOrNull(String extensionPoint, String bizScenario) {
        return extensionRepo.get(GXExtensionCoordinate.valueOf(extensionPoint, bizScenario));
    }

    @NullMarked
    public Optional<GXExtensionPoint> findExtension(String extensionPoint, String bizScenario) {
        return Optional.ofNullable(findExtensionOrNull(extensionPoint, bizScenario));
    }

    public void registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension) {
        Objects.requireNonNull(coordinate, "Extension coordinate cannot be null");
        Objects.requireNonNull(extension, "Extension implementation cannot be null");
        if (extensionRepo.putIfAbsent(coordinate, extension) != null) {
            throw new IllegalStateException("Extension coordinate already exists: " + coordinate);
        }
    }

    @NullMarked
    public Optional<GXExtensionPoint> registerExtensionIfAbsent(GXExtensionCoordinate coordinate, GXExtensionPoint extension) {
        Objects.requireNonNull(coordinate, "Extension coordinate cannot be null");
        Objects.requireNonNull(extension, "Extension implementation cannot be null");
        return Optional.ofNullable(extensionRepo.putIfAbsent(coordinate, extension));
    }

    public int size() {
        return extensionRepo.size();
    }
}
