package cn.maple.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXExtensionRepositoryTest {
    @Test
    void getExtensionRepoReturnsReadOnlySnapshot() {
        GXExtensionRepository repository = new GXExtensionRepository();
        GXExtensionCoordinate coordinate = coordinate("biz");
        GXExtensionCoordinate laterCoordinate = coordinate("later");

        assertThrows(UnsupportedOperationException.class,
                () -> repository.getExtensionRepo().put(coordinate, new RepositoryTestExtension()));

        repository.registerExtension(coordinate, new RepositoryTestExtension());
        var snapshot = repository.getExtensionRepo();
        repository.registerExtension(laterCoordinate, new RepositoryTestExtension());

        assertTrue(snapshot.containsKey(coordinate));
        assertFalse(snapshot.containsKey(laterCoordinate));
        assertTrue(repository.findExtension(laterCoordinate).isPresent());
    }

    @Test
    void registerExtensionDoesNotOverwriteExistingExtension() {
        GXExtensionRepository repository = new GXExtensionRepository();
        GXExtensionCoordinate coordinate = coordinate("biz");
        RepositoryTestExtension first = new RepositoryTestExtension();
        RepositoryTestExtension second = new RepositoryTestExtension();

        repository.registerExtension(coordinate, first);

        assertThrows(IllegalStateException.class, () -> repository.registerExtension(coordinate, second));
        assertSame(first, repository.findExtension(coordinate).orElseThrow());
    }

    @Test
    void registerExtensionRejectsNulls() {
        GXExtensionRepository repository = new GXExtensionRepository();
        GXExtensionCoordinate coordinate = coordinate("biz");

        assertThrows(NullPointerException.class, () -> repository.registerExtension(null, new RepositoryTestExtension()));
        assertThrows(NullPointerException.class, () -> repository.registerExtension(coordinate, null));
        assertFalse(repository.findExtension(coordinate).isPresent());
    }

    @Test
    void registerIfAbsentKeepsExistingExtension() {
        GXExtensionRepository repository = new GXExtensionRepository();
        GXExtensionCoordinate coordinate = coordinate("biz");
        RepositoryTestExtension first = new RepositoryTestExtension();
        RepositoryTestExtension second = new RepositoryTestExtension();

        assertFalse(repository.registerExtensionIfAbsent(coordinate, first).isPresent());
        assertTrue(repository.registerExtensionIfAbsent(coordinate, second).isPresent());

        assertSame(first, repository.findExtension(coordinate).orElseThrow());
    }

    private GXExtensionCoordinate coordinate(String bizId) {
        return GXExtensionCoordinate.valueOf(RepositoryTestExtPoint.class, GXBizScenario.valueOf(bizId));
    }

    private interface RepositoryTestExtPoint extends GXExtensionPoint {
    }

    private static class RepositoryTestExtension implements RepositoryTestExtPoint {
    }
}
