package com.clockworktown.campboard.storage;

import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.data.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BoardStorageTest {
    @TempDir Path root;
    private final CampBoardConfig config = new CampBoardConfig();

    private BoardState board(String id, int stored) {
        BoardState state = new BoardState();
        Project project = new Project(id, "Test", "Description", UUID.randomUUID());
        MaterialStack material = new MaterialStack("minecraft:stone", 128);
        material.contribute(stored);
        project.materials().put(material.itemId(), material);
        project.tasks().add(new ProjectTask("roof", "Build roof"));
        state.projects().add(project);
        return state;
    }

    @Test void roundTripPreservesBoardData() throws Exception {
        BoardStorage storage = new BoardStorage(root);
        storage.saveBoard("board_a", board("house", 64), config);
        Project loaded = new BoardStorage(root).loadBoard("board_a").projects().getFirst();
        assertEquals("house", loaded.id());
        assertEquals(64, loaded.materials().get("minecraft:stone").stored());
        assertEquals("Build roof", loaded.tasks().getFirst().title());
    }

    @Test void fullBackupRestoresPhysicalAndLegacyBoards() throws Exception {
        BoardStorage storage = new BoardStorage(root);
        storage.saveBoard(board("legacy", 16), config);
        storage.saveBoard("board_a", board("house", 64), config);
        Path backup = storage.createBackup(config);
        storage.saveBoard(new BoardState(), config);
        storage.saveBoard("board_a", new BoardState(), config);
        storage.saveBoard("board_b", board("other", 32), config);
        storage.restore(backup.getFileName().toString());
        assertEquals("legacy", storage.loadBoard().projects().getFirst().id());
        assertEquals(64, storage.loadBoard("board_a").projects().getFirst().materials().get("minecraft:stone").stored());
        assertEquals("other", storage.loadBoard("board_b").projects().getFirst().id());
    }

    @Test void automaticBackupRestoresOnlyItsBoard() throws Exception {
        BoardStorage storage = new BoardStorage(root);
        storage.saveBoard("board_a", board("house", 64), config);
        storage.saveBoard("board_a", new BoardState(), config);
        Path backup;
        try (var files = Files.list(root.resolve("campboard/backups"))) { backup = files.filter(Files::isRegularFile).findFirst().orElseThrow(); }
        storage.saveBoard("board_b", board("other", 32), config);
        storage.restore(backup.getFileName().toString());
        assertEquals("house", storage.loadBoard("board_a").projects().getFirst().id());
        assertEquals("other", storage.loadBoard("board_b").projects().getFirst().id());
    }

    @Test void damagedBoardIsNotSilentlyLoadedAsEmptyAndCanBeRestored() throws Exception {
        BoardStorage storage = new BoardStorage(root);
        storage.saveBoard("board_a", board("house", 64), config);
        Path backup = storage.createBackup(config);
        Path file = root.resolve("campboard/boards/board_a.json");
        Files.writeString(file, "broken JSON");
        assertThrows(java.io.IOException.class, () -> storage.loadBoard("board_a"));
        assertEquals("broken JSON", Files.readString(file));
        storage.restore(backup.getFileName().toString());
        assertEquals("house", storage.loadBoard("board_a").projects().getFirst().id());
        try (var files = Files.walk(root.resolve("campboard/backups"))) {
            assertTrue(files.filter(Files::isRegularFile).anyMatch(path -> {
                try { return Files.readString(path).equals("broken JSON"); }
                catch (java.io.IOException exception) { return false; }
            }));
        }
    }

    @Test void legacyBackupStillRestores() throws Exception {
        BoardStorage storage = new BoardStorage(root);
        Path backups = root.resolve("campboard/backups");
        Files.createDirectories(backups);
        Files.writeString(backups.resolve("old.json"), BoardJson.toJson(board("legacy", 4)));
        storage.restore("old.json");
        assertEquals("legacy", storage.loadBoard().projects().getFirst().id());
    }

    @Test void invalidSnapshotIsRejectedBeforeChangingFiles() throws Exception {
        BoardStorage storage = new BoardStorage(root);
        storage.saveBoard("board_a", board("house", 64), config);
        Path backups = root.resolve("campboard/backups");
        Files.createDirectories(backups);
        Files.writeString(backups.resolve("bad.json"), "{\"backupFormat\":2,\"boards\":{\"../outside\":{}}}");
        assertThrows(java.io.IOException.class, () -> storage.restore("bad.json"));
        assertEquals("house", storage.loadBoard("board_a").projects().getFirst().id());
        assertThrows(java.io.IOException.class, () -> storage.restore("../../outside.json"));
    }

    @Test void snapshotRestoresConfigAndRetentionIsPerBoard() throws Exception {
        BoardStorage storage = new BoardStorage(root);
        config.setBackupRetention(1);
        config.setBreakable(false);
        storage.saveConfig(config);
        storage.saveBoard("board_a", board("house", 1), config);
        Path backup = storage.createBackup(config);
        for (int count = 2; count < 5; count++) storage.saveBoard("board_a", board("house", count), config);
        config.setBreakable(true);
        storage.saveConfig(config);
        storage.restore(backup.getFileName().toString());
        assertFalse(storage.loadConfig().breakable());
        try (var files = Files.list(root.resolve("campboard/backups"))) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().startsWith("board-board_a--")).count());
        }
    }
}
