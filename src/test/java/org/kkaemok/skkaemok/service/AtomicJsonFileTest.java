package org.kkaemok.skkaemok.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AtomicJsonFileTest {
    @TempDir Path directory;

    @Test
    void replacesSnapshotsAndLeavesNoTemporaryFiles() throws Exception {
        Path file = directory.resolve("viewer-names.json");
        AtomicJsonFile.write(file, "{\"name\":\"한글\"}");
        AtomicJsonFile.write(file, "{}");
        assertEquals("{}", Files.readString(file));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void failedReplacementPreservesExistingTargetAndCleansTemporaryFile() throws Exception {
        Path target = Files.createDirectory(directory.resolve("occupied"));
        Files.writeString(target.resolve("existing.json"), "old");
        assertThrows(java.io.IOException.class, () -> AtomicJsonFile.write(target, "new"));
        assertEquals("old", Files.readString(target.resolve("existing.json")));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
