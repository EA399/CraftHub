package de.crafthub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void profilesPersistAcrossRestartAndDeleteTheirFiles() throws IOException {
        InstanceRepository repository = new InstanceRepository(temporaryDirectory);
        GameInstance created = repository.create("  Mein Modpack  ", "1.21.8", GameInstance.Loader.FABRIC);
        Path mod = repository.modsDirectory(created).resolve("example.jar");
        Files.writeString(mod, "test");

        InstanceRepository reopened = new InstanceRepository(temporaryDirectory);
        assertEquals(created, reopened.list().get(0));
        assertTrue(Files.exists(mod));

        reopened.delete(created.id());
        assertTrue(reopened.list().isEmpty());
        assertFalse(Files.exists(mod.getParent()));
    }

    @Test
    void profileNamesAreUniqueIgnoringCase() throws IOException {
        InstanceRepository repository = new InstanceRepository(temporaryDirectory);
        repository.create("Survival", "1.21.8", GameInstance.Loader.VANILLA);

        assertThrows(IllegalArgumentException.class,
                () -> repository.create("survival", "1.21.8", GameInstance.Loader.FABRIC));
    }

    @Test
    void copiedProfileGetsUniqueIdentityAndCopiesItsMods() throws IOException {
        InstanceRepository repository = new InstanceRepository(temporaryDirectory);
        GameInstance created = repository.create("Mein Modpack", "1.8.9", GameInstance.Loader.FORGE);
        Path mod = repository.modsDirectory(created).resolve("beispiel.jar");
        Files.writeString(mod, "test");

        GameInstance copy = repository.copy(created.id());

        assertNotEquals(created.id(), copy.id());
        assertEquals("Mein Modpack (Kopie)", copy.name());
        assertEquals(created.version(), copy.version());
        assertEquals(created.loader(), copy.loader());
        assertEquals("test", Files.readString(repository.modsDirectory(copy).resolve(mod.getFileName())));
        assertEquals("Mein Modpack (Kopie 2)", repository.copy(created.id()).name());
    }
}