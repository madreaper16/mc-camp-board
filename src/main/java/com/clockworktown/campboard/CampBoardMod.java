package com.clockworktown.campboard;

import com.clockworktown.campboard.block.CampBoardBlock;
import com.clockworktown.campboard.block.CampBoardBlockEntity;
import com.clockworktown.campboard.command.CampBoardCommands;
import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.data.BoardState;
import com.clockworktown.campboard.network.BoardActionHandler;
import com.clockworktown.campboard.network.BoardActionPayload;
import com.clockworktown.campboard.network.OpenBoardPayload;
import com.clockworktown.campboard.resource.CraftingEnabledResourceCondition;
import com.clockworktown.campboard.storage.BoardStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class CampBoardMod implements ModInitializer {
    public static final String MOD_ID = "camp_board";
    public static final Identifier CAMP_BOARD_ID = id("camp_board");
    public static final Block CAMP_BOARD_BLOCK = registerBlock();
    public static final Item CAMP_BOARD_ITEM = registerBlockItem();
    public static final BlockEntityType<CampBoardBlockEntity> CAMP_BOARD_BLOCK_ENTITY = registerBlockEntity();

    private static BoardStorage storage;
    private static BoardState boardState = new BoardState();
    private static final Map<String, BoardState> boardStates = new HashMap<>();
    private static CampBoardConfig config = new CampBoardConfig();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(OpenBoardPayload.TYPE, OpenBoardPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BoardActionPayload.TYPE, BoardActionPayload.CODEC);
        ResourceConditions.register(CraftingEnabledResourceCondition.TYPE);
        ServerPlayNetworking.registerGlobalReceiver(BoardActionPayload.TYPE, BoardActionHandler::handle);
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
                return new BoardState();
            }
        });
    }

    public static CampBoardConfig config() {
        return config;
    }

    public static void load(MinecraftServer server) throws IOException {
        storage = new BoardStorage(serverConfigPath(server));
        config = storage.loadConfig();
        boardState = storage.loadBoard();
        boardStates.clear();
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
        if (storage != null && boardId != null && !boardId.isBlank()) {
            storage.saveBoard(boardId, boardState(boardId), config);
            storage.saveConfig(config);
        }
    }

    public static Path importDirectory(MinecraftServer server) {
        return serverConfigPath(server).resolve("campboard").resolve("imports");
    }

    public static void saveQuietly() {
        try {
            save();
        } catch (IOException ignored) {
        }
    }

    public static Path backup() throws IOException {
        ensureStorage();
        return storage.createBackup(config);
    }

    public static Path export() throws IOException {
        ensureStorage();
        return storage.export(boardState);
    }

    public static void restore(String backupName) throws IOException {
        ensureStorage();
        boardState = storage.restore(backupName);
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
