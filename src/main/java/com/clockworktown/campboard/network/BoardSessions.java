package com.clockworktown.campboard.network;

import com.clockworktown.campboard.CampBoardMod;
import com.clockworktown.campboard.block.CampBoardBlockEntity;
import com.clockworktown.campboard.storage.BoardJson;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BoardSessions {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private BoardSessions() { }

    public static void open(ServerPlayer player, BlockPos position, String boardId) {
        if (!ServerPlayNetworking.canSend(player, OpenBoardPayload.TYPE)) {
            player.sendSystemMessage(Component.literal("Install the matching Camp Board version on your client."));
            return;
        }
        Session session = new Session(boardId, position.immutable(), player.level().dimension().identifier().toString());
        SESSIONS.put(player.getUUID(), session);
        try {
            sendConfig(player);
            send(player, boardId, true);
        } catch (RuntimeException exception) {
            close(player);
            player.sendSystemMessage(Component.literal("Unable to open Camp Board: " + exception.getMessage()));
        }
    }

    public static void require(ServerPlayer player, String boardId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.boardId().equals(boardId) || !valid(player, session)) {
            close(player);
            throw new IllegalArgumentException("Open a nearby Camp Board before changing it.");
        }
    }

    private static boolean valid(ServerPlayer player, Session session) {
        if (!player.isAlive() || !player.level().dimension().identifier().toString().equals(session.dimension())
                || player.distanceToSqr(session.position().getX() + 0.5, session.position().getY() + 0.5, session.position().getZ() + 0.5) > 64
                || !player.level().hasChunkAt(session.position())) return false;
        if (!(player.level().getBlockEntity(session.position()) instanceof CampBoardBlockEntity entity)) return false;
        String actual = CampBoardMod.config().allBoardsGlobal() ? "global" : entity.boardId();
        return actual.equals(session.boardId());
    }

    public static void refresh(MinecraftServer server, String boardId) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            Session session = SESSIONS.get(viewer.getUUID());
            if (session == null || !session.boardId().equals(boardId)) continue;
            if (!valid(viewer, session)) { close(viewer); continue; }
            try { send(viewer, boardId, false); }
            catch (RuntimeException exception) {
                viewer.sendSystemMessage(Component.literal("Camp Board refresh failed: " + exception.getMessage()));
            }
        }
    }

    private static void send(ServerPlayer player, String boardId, boolean opening) {
        var chunks = BoardTransfer.split(BoardJson.forClient(CampBoardMod.boardState(boardId)));
        String transfer = UUID.randomUUID().toString();
        for (int index = 0; index < chunks.size(); index++) {
            ServerPlayNetworking.send(player, new OpenBoardPayload(boardId, transfer, opening, index, chunks.size(), chunks.get(index)));
        }
    }

    public static void sendConfig(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, ConfigPayload.TYPE)) {
            boolean admin = player.level().getServer().getPlayerList().isOp(player.nameAndId());
            ServerPlayNetworking.send(player, new ConfigPayload(CampBoardMod.config().toJson(), admin));
        }
    }

    public static void close(ServerPlayer player) { SESSIONS.remove(player.getUUID()); }
    public static void close(ServerPlayer player, String boardId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.boardId().equals(boardId)) close(player);
    }
    public static void clear() { SESSIONS.clear(); }
    private record Session(String boardId, BlockPos position, String dimension) { }
}
