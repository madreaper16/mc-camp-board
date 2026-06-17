package com.clockworktown.campboard.storage;

import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.data.BoardState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class BoardStorage {
    public static final String DATA_FILE_NAME = "projects.json";
    public static final String CONFIG_FILE_NAME = "config.json";

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .setPrettyPrinting()
            .create();
    private final Path directory;
    private final Path backupDirectory;

    public BoardStorage(Path serverConfigDirectory) {
        this.directory = serverConfigDirectory.resolve("campboard");
        this.backupDirectory = directory.resolve("backups");
    }

    public BoardState loadBoard() throws IOException {
        return loadBoardFile(directory.resolve(DATA_FILE_NAME));
    }

    public BoardState loadBoard(String boardId) throws IOException {
        return loadBoardFile(boardFile(boardId));
    }

    public List<String> boardIds() throws IOException {
        Path boardsDirectory = directory.resolve("boards");
        if (!Files.exists(boardsDirectory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(boardsDirectory)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .toList();
        }
    }

    private BoardState loadBoardFile(Path dataFile) throws IOException {
        if (!Files.exists(dataFile)) {
            return new BoardState();
        }

        return BoardJson.fromJson(Files.readString(dataFile));
    }

    public CampBoardConfig loadConfig() throws IOException {
        Path configFile = directory.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(configFile)) {
            CampBoardConfig config = new CampBoardConfig();
            saveConfig(config);
            return config;
        }

        try (Reader reader = Files.newBufferedReader(configFile)) {
            CampBoardConfig loaded = gson.fromJson(reader, CampBoardConfig.class);
            return loaded == null ? new CampBoardConfig() : loaded;
        }
    }

    public void saveBoard(BoardState state, CampBoardConfig config) throws IOException {
        saveBoardFile(directory.resolve(DATA_FILE_NAME), state, config);
    }

    public void saveBoard(String boardId, BoardState state, CampBoardConfig config) throws IOException {
        saveBoardFile(boardFile(boardId), state, config);
    }

    private void saveBoardFile(Path dataFile, BoardState state, CampBoardConfig config) throws IOException {
        Files.createDirectories(directory);
        Files.createDirectories(dataFile.getParent());
        if (Files.exists(dataFile)) {
            createBackup(config);
        }

        Path tempFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempFile)) {
            writer.write(BoardJson.toJson(state));
        }
        Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        pruneBackups(config.backupRetention());
    }

    public void saveConfig(CampBoardConfig config) throws IOException {
        Files.createDirectories(directory);
        try (Writer writer = Files.newBufferedWriter(directory.resolve(CONFIG_FILE_NAME))) {
            gson.toJson(config, writer);
        }
    }

    public Path createBackup(CampBoardConfig config) throws IOException {
        Files.createDirectories(backupDirectory);
        Path dataFile = directory.resolve(DATA_FILE_NAME);
        if (!Files.exists(dataFile)) {
            return dataFile;
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path backupFile = backupDirectory.resolve("projects-" + timestamp + ".json");
        Files.copy(dataFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        pruneBackups(config.backupRetention());
        return backupFile;
    }

    public Path export(BoardState state) throws IOException {
        Files.createDirectories(directory);
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path exportFile = directory.resolve("export-" + timestamp + ".json");
        try (Writer writer = Files.newBufferedWriter(exportFile)) {
            writer.write(BoardJson.toJson(state));
        }
        return exportFile;
    }

    public BoardState restore(String backupName) throws IOException {
        Path backupFile = backupDirectory.resolve(backupName).normalize();
        if (!backupFile.startsWith(backupDirectory) || !Files.exists(backupFile)) {
            throw new IOException("Backup not found: " + backupName);
        }

        BoardState restored = BoardJson.fromJson(Files.readString(backupFile));
        Files.createDirectories(directory);
        Files.copy(backupFile, directory.resolve(DATA_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        return restored;
    }

    private void pruneBackups(int retention) throws IOException {
        if (!Files.exists(backupDirectory)) {
            return;
        }

        try (Stream<Path> stream = Files.list(backupDirectory)) {
            List<Path> backups = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .toList();

            for (int index = retention; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private Path boardFile(String boardId) {
        String safe = boardId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return directory.resolve("boards").resolve(safe + ".json");
    }
}
