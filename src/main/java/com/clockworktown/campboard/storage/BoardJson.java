package com.clockworktown.campboard.storage;

import com.clockworktown.campboard.data.BoardState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;

public final class BoardJson {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .setPrettyPrinting()
            .create();

    private BoardJson() {
    }

    public static String toJson(BoardState state) {
        return GSON.toJson(state);
    }

    public static BoardState fromJson(String json) {
        BoardState state = GSON.fromJson(json, BoardState.class);
        return state == null ? new BoardState() : state;
    }
}
