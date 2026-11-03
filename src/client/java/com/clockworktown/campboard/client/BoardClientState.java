package com.clockworktown.campboard.client;

import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.CampBoardMod;
import com.clockworktown.campboard.network.BoardActionPayload;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class BoardClientState {
    private static CampBoardConfig config = new CampBoardConfig();
    private static boolean editable;
    private BoardClientState() { }
    public static CampBoardConfig config() { return config; }
    public static boolean editable() { return editable && connected(); }
    public static boolean connected() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(BoardActionPayload.TYPE);
    }
    public static void update(CampBoardConfig updated, boolean canEdit) { config = updated; editable = canEdit; CampBoardMod.clientConfig(updated); }
    public static void reset() { update(new CampBoardConfig(), false); }
    public static void requestConfig() {
        if (connected()) {
            JsonObject action = new JsonObject();
            action.addProperty("type", "request_config");
            ClientPlayNetworking.send(new BoardActionPayload(action.toString()));
        }
    }
}
