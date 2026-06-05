package com.clockworktown.campboard.client;

import com.clockworktown.campboard.data.BoardState;
import com.clockworktown.campboard.network.OpenBoardPayload;
import com.clockworktown.campboard.storage.BoardJson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CampBoardClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenBoardPayload.TYPE, (payload, context) -> {
            BoardState boardState = BoardJson.fromJson(payload.boardJson());
            context.client().execute(() -> {
                if (context.client().screen instanceof CampBoardScreen screen) {
                    screen.updateBoardState(payload.boardId(), boardState);
                } else {
                    context.client().setScreen(new CampBoardScreen(payload.boardId(), boardState));
                }
            });
        });
    }
}
