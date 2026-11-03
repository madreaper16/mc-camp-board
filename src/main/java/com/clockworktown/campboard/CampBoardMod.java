package com.clockworktown.campboard;

import com.clockworktown.campboard.block.CampBoardBlock;
import com.clockworktown.campboard.block.CampBoardBlockEntity;
import com.clockworktown.campboard.command.CampBoardCommands;
import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.data.BoardState;
import com.clockworktown.campboard.data.Project;
import com.clockworktown.campboard.data.ProjectLookup;
import com.clockworktown.campboard.network.BoardActionHandler;
import com.clockworktown.campboard.network.BoardActionPayload;
import com.clockworktown.campboard.network.OpenBoardPayload;
import com.clockworktown.campboard.network.ConfigPayload;
import com.clockworktown.campboard.network.BoardSessions;
import com.clockworktown.campboard.resource.CraftingEnabledResourceCondition;
import com.clockworktown.campboard.storage.BoardStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CampBoardMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("camp_board");
    public static final String MOD_ID = "camp_board";
    public static final Identifier CAMP_BOARD_ID = id("camp_board");
    public static final Block CAMP_BOARD_BLOCK = registerBlock();
    public static final Item CAMP_BOARD_ITEM = registerBlockItem();
    public static final BlockEntityType<CampBoardBlockEntity> CAMP_BOARD_BLOCK_ENTITY = registerBlockEntity();

    private static BoardStorage storage;
    private static BoardState boardState = new BoardState();
    private static final Map<String, BoardState> boardStates = new HashMap<>();
    private static CampBoardConfig config = new CampBoardConfig();
    private static CampBoardConfig clientConfig = new CampBoardConfig();
    private static MinecraftServer activeServer;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(OpenBoardPayload.TYPE, OpenBoardPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BoardActionPayload.TYPE, BoardActionPayload.CODEC);
        ResourceConditions.register(CraftingEnabledResourceCondition.TYPE);
        ServerPlayNetworking.registerGlobalReceiver(BoardActionPayload.TYPE, BoardActionHandler::handle);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BoardSessions.close(handler.player));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(new ItemStack(CAMP_BOARD_ITEM), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> entries.accept(new ItemStack(CAMP_BOARD_ITEM), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !state.is(CAMP_BOARD_BLOCK) || config.breakable());
        CampBoardCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                load(server);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load Camp Board data.", exception);
            }
        });
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> saveQuietly());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveQuietly());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            storage = null;
            activeServer = null;
            boardState = new BoardState();
            boardStates.clear();
            config = new CampBoardConfig();
            BoardSessions.clear();
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static BoardState boardState() {
        return boardState;
    }

    public static BoardState boardState(String boardId) {
        if (boardId == null || boardId.isBlank()) {
            return boardState;
        }
        return boardStates.computeIfAbsent(boardId, id -> {
            try {
                return storage == null ? new BoardState() : storage.loadBoard(id);
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not load board " + id + "; its file has not been changed.", exception);
            }
        });
    }

    public static CampBoardConfig config() {
        return config;
    }

    public static CampBoardConfig config(net.minecraft.world.level.Level level) {
        return level.isClientSide() ? clientConfig : config;
    }

    public static void clientConfig(CampBoardConfig updated) { clientConfig = updated; }

    public static void load(MinecraftServer server) throws IOException {
        BoardStorage loadedStorage = new BoardStorage(serverConfigPath(server));
        CampBoardConfig loadedConfig = loadedStorage.loadConfig();
        BoardState loadedState = loadedStorage.loadBoard();
        storage = loadedStorage;
        config = loadedConfig;
        boardState = loadedState;
        boardStates.clear();
        activeServer = server;
        BoardSessions.clear();
        java.nio.file.Files.createDirectories(importDirectory(server));
    }

    public static void save() throws IOException {
        if (storage != null) {
            storage.saveBoard(boardState, config);
            for (Map.Entry<String, BoardState> entry : boardStates.entrySet()) {
                storage.saveBoard(entry.getKey(), entry.getValue(), config);
            }
            storage.saveConfig(config);
        }
    }

    public static void saveBoard(String boardId) throws IOException {
        ensureStorage();
        if (boardId == null || boardId.isBlank()) storage.saveBoard(boardState, config);
        else storage.saveBoard(boardId, boardState(boardId), config);
    }

    public static Optional<ProjectMatch> findProject(String projectId) {
        Map<String, BoardState> boards = new LinkedHashMap<>();
        for (String id : knownBoardIds()) boards.put(id, boardState(id));
        return ProjectLookup.unique(boards, projectId).map(match -> new ProjectMatch(match.boardId(), match.state(), match.project()));
    }

    public static Set<String> knownBoardIds() {
        Set<String> ids = new LinkedHashSet<>(boardStates.keySet());
        ids.add("");
        if (storage != null) {
            try { ids.addAll(storage.boardIds()); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
        return ids;
    }

    public static Optional<ProjectMatch> findProject(String projectId, String boardId) {
        if (!knownBoardIds().contains(boardId)) return Optional.empty();
        BoardState state = boardState(boardId);
        return state.findProject(projectId).map(project -> new ProjectMatch(boardId, state, project));
    }

    public static void saveProjectMatch(ProjectMatch match) throws IOException {
        saveBoard(match.boardId());
        if (activeServer != null) BoardSessions.refresh(activeServer, match.boardId());
    }

    public static void replaceBoard(String id, BoardState state) {
        if (id == null || id.isBlank()) boardState = state;
        else boardStates.put(id, state);
    }

    public static void updateConfig(CampBoardConfig updated) throws IOException {
        ensureStorage();
        storage.saveConfig(updated);
        boolean modeChanged = config.allBoardsGlobal() != updated.allBoardsGlobal();
        config = updated;
        if (modeChanged) BoardSessions.clear();
        if (activeServer != null) {
            for (var player : activeServer.getPlayerList().getPlayers()) BoardSessions.sendConfig(player);
        }
    }

    public static Path importDirectory(MinecraftServer server) {
        return serverConfigPath(server).resolve("campboard").resolve("imports");
    }

    public static void saveQuietly() {
        try {
            save();
        } catch (IOException exception) {
            LOGGER.error("Camp Board data could not be saved", exception);
        }
    }

    public static Path backup() throws IOException {
        ensureStorage();
        save();
        return storage.createBackup(config);
    }

    public static Path export() throws IOException {
        ensureStorage();
        save();
        return storage.export(boardState);
    }

    public static void restore(String backupName) throws IOException {
        ensureStorage();
        boardState = storage.restore(backupName);
        config = storage.loadConfig();
        boardStates.clear();
        BoardSessions.clear();
        if (activeServer != null) for (var player : activeServer.getPlayerList().getPlayers()) BoardSessions.sendConfig(player);
    }

    public static void reload(MinecraftServer server) throws IOException {
        load(server);
    }

    public static Path serverConfigPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
    }

    private static void ensureStorage() {
        if (storage == null) {
            throw new IllegalStateException("Camp Board storage has not been initialized yet.");
        }
    }

    public record ProjectMatch(String boardId, BoardState state, Project project) {
    }

    private static Block registerBlock() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, CAMP_BOARD_ID);
        Block block = new CampBoardBlock(BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(MapColor.WOOD)
                .strength(1.5F)
                .sound(SoundType.WOOD)
                .noOcclusion());
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    private static Item registerBlockItem() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CAMP_BOARD_ID);
        Item item = new BlockItem(CAMP_BOARD_BLOCK, new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static BlockEntityType<CampBoardBlockEntity> registerBlockEntity() {
        ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id("camp_board"));
        BlockEntityType<CampBoardBlockEntity> type = FabricBlockEntityTypeBuilder.create(CampBoardBlockEntity::new, CAMP_BOARD_BLOCK).build();
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
    }
}
