package com.clockworktown.campboard.storage;

import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.data.BoardState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class BoardStorage {
    public static final String DATA_FILE_NAME = "projects.json";
    public static final String CONFIG_FILE_NAME = "config.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    private final Path backupDirectory;

    public BoardStorage(Path serverConfigDirectory) {
        directory = serverConfigDirectory.resolve("campboard");
        backupDirectory = directory.resolve("backups");
    }

    public BoardState loadBoard() throws IOException { return loadBoardFile(boardFile("")); }
    public BoardState loadBoard(String boardId) throws IOException { return loadBoardFile(boardFile(boardId)); }

    public List<String> boardIds() throws IOException {
        Path boards = directory.resolve("boards");
        if (!Files.exists(boards)) return List.of();
        try (Stream<Path> stream = Files.list(boards)) {
            return stream.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5)).sorted().toList();
        }
    }

    private BoardState loadBoardFile(Path file) throws IOException {
        if (!Files.exists(file)) return new BoardState();
        try { return BoardJson.fromJson(Files.readString(file)); }
        catch (RuntimeException exception) {
            throw new IOException("Cannot read " + file.getFileName() + ": " + exception.getMessage(), exception);
        }
    }

    public CampBoardConfig loadConfig() throws IOException {
        Path file = directory.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(file)) {
            CampBoardConfig config = new CampBoardConfig();
            saveConfig(config);
            return config;
        }
        try { return CampBoardConfig.fromJson(Files.readString(file)); }
        catch (RuntimeException exception) { throw new IOException("Cannot read config.json", exception); }
    }

    public void saveBoard(BoardState state, CampBoardConfig config) throws IOException { saveBoard("", state, config); }

    public void saveBoard(String boardId, BoardState state, CampBoardConfig config) throws IOException {
        Path file = boardFile(boardId);
        String contents = BoardJson.toJson(state);
        if (Files.exists(file)) {
            String previous = Files.readString(file);
            if (previous.equals(contents)) return;
            JsonObject snapshot = snapshot();
            snapshot.getAsJsonObject("boards").add(boardId, JsonParser.parseString(previous));
            writeBackup(snapshot, config, "board-" + (boardId.isEmpty() ? "legacy" : boardId) + "--");
        }
        atomicWrite(file, contents);
    }

    public void saveConfig(CampBoardConfig config) throws IOException {
        atomicWrite(directory.resolve(CONFIG_FILE_NAME), config.toJson());
    }

    public Path createBackup(CampBoardConfig config) throws IOException { return writeBackup(allBoards(), config, "snapshot-"); }

    public Path export(BoardState ignored) throws IOException {
        Path file = directory.resolve("export-" + timestamp() + ".json");
        atomicWrite(file, gson.toJson(allBoards()));
        return file;
    }

    public BoardState restore(String backupName) throws IOException {
        Path backup = backupDirectory.resolve(backupName).normalize();
        if (!backup.startsWith(backupDirectory.normalize()) || !Files.isRegularFile(backup)) {
            throw new IOException("Backup not found: " + backupName);
        }
        Map<String, String> restored = new LinkedHashMap<>();
        CampBoardConfig restoredConfig = null;
        try {
            JsonObject contents = JsonParser.parseString(Files.readString(backup)).getAsJsonObject();
            if (contents.has("backupFormat")) {
                if (contents.get("backupFormat").getAsInt() != 2) throw new IllegalArgumentException("Unknown backup format");
                for (var entry : contents.getAsJsonObject("boards").entrySet()) {
                    boardFile(entry.getKey());
                    restored.put(entry.getKey(), BoardJson.toJson(BoardJson.fromJson(entry.getValue().toString())));
                }
                if (contents.has("config")) restoredConfig = CampBoardConfig.fromJson(contents.get("config").toString());
            } else restored.put("", BoardJson.toJson(BoardJson.fromJson(contents.toString())));
        } catch (RuntimeException exception) { throw new IOException("Invalid backup: " + backupName, exception); }
        // Keep raw recovery copies, including files that are too damaged to parse.
        Path recovery = backupDirectory.resolve("recovery-" + timestamp());
        if (Files.exists(boardFile(""))) copyRecovery(boardFile(""), recovery.resolve(DATA_FILE_NAME));
        for (String id : boardIds()) copyRecovery(boardFile(id), recovery.resolve("boards").resolve(id + ".json"));
        Path currentConfig = directory.resolve(CONFIG_FILE_NAME);
        if (Files.exists(currentConfig)) copyRecovery(currentConfig, recovery.resolve(CONFIG_FILE_NAME));
        for (var entry : restored.entrySet()) atomicWrite(boardFile(entry.getKey()), entry.getValue());
        if (restoredConfig != null) saveConfig(restoredConfig);
        return loadBoard();
    }

    private JsonObject snapshot() {
        JsonObject object = new JsonObject();
        object.addProperty("backupFormat", 2);
        object.add("boards", new JsonObject());
        return object;
    }

    private JsonObject allBoards() throws IOException {
        JsonObject object = snapshot();
        JsonObject boards = object.getAsJsonObject("boards");
        if (Files.exists(boardFile(""))) boards.add("", JsonParser.parseString(BoardJson.toJson(loadBoard())));
        for (String id : boardIds()) boards.add(id, JsonParser.parseString(BoardJson.toJson(loadBoard(id))));
        Path configFile = directory.resolve(CONFIG_FILE_NAME);
        if (Files.exists(configFile)) object.add("config", JsonParser.parseString(Files.readString(configFile)));
        return object;
    }

    private Path writeBackup(JsonObject contents, CampBoardConfig config, String prefix) throws IOException {
        Path file = backupDirectory.resolve(prefix + timestamp() + ".json");
        atomicWrite(file, gson.toJson(contents));
        try (Stream<Path> stream = Files.list(backupDirectory)) {
            List<Path> files = stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::lastModified).reversed()).toList();
            for (int index = Math.max(1, config.backupRetention()); index < files.size(); index++) Files.deleteIfExists(files.get(index));
        }
        return file;
    }

    private Instant lastModified(Path file) {
        try { return Files.getLastModifiedTime(file).toInstant(); }
        catch (IOException exception) { return Instant.EPOCH; }
    }

    private static String timestamp() { return Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID(); }

    private static void atomicWrite(Path file, String contents) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, contents);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void copyRecovery(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }

    private Path boardFile(String id) {
        if (id.isEmpty()) return directory.resolve(DATA_FILE_NAME);
        if (!id.matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid board ID");
        return directory.resolve("boards").resolve(id + ".json");
    }
}
