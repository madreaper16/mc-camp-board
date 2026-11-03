package com.clockworktown.campboard.client;

import com.clockworktown.campboard.data.BoardState;
import com.clockworktown.campboard.network.OpenBoardPayload;
import com.clockworktown.campboard.network.ConfigPayload;
import com.clockworktown.campboard.network.BoardTransfer;
import com.clockworktown.campboard.config.CampBoardConfig;
import com.clockworktown.campboard.compat.modmenu.CampBoardConfigScreen;
import com.clockworktown.campboard.storage.BoardJson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

public class CampBoardClient implements ClientModInitializer {
    private final BoardTransfer transfer = new BoardTransfer();
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenBoardPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    var result = transfer.accept(payload.boardId(), payload.transferId(), payload.opening(), payload.index(), payload.count(), payload.fragment(), System.nanoTime() / 1_000_000);
                    if (result == null) return;
                    BoardState state = BoardJson.fromJson(result.json());
                    if (context.client().gui.screen() instanceof CampBoardScreen screen && screen.boardId().equals(result.boardId())) {
                        screen.updateBoardState(result.boardId(), state);
                    } else if (result.opening()) {
                        context.client().gui.setScreen(new CampBoardScreen(result.boardId(), state));
                    }
                } catch (RuntimeException exception) {
                    transfer.reset();
                    if (context.client().player != null) context.client().player.sendSystemMessage(Component.literal("Camp Board update failed: " + exception.getMessage()));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            BoardClientState.update(CampBoardConfig.fromJson(payload.json()), payload.editable());
            if (context.client().gui.screen() instanceof CampBoardConfigScreen screen) screen.refreshConfig();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            transfer.reset();
            BoardClientState.reset();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BoardClientState.requestConfig());
    }
}
