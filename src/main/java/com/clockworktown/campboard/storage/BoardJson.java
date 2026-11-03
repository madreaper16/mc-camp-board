package com.clockworktown.campboard.storage;

import com.clockworktown.campboard.data.BoardState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
        if (state == null || state.schemaVersion() != 1 || state.projects() == null || state.suggestions() == null) {
            throw new IllegalArgumentException("Invalid or unsupported Camp Board data.");
        }
        for (var project : state.projects()) {
            if (project == null || project.id() == null || project.title() == null || project.description() == null
                    || project.status() == null || project.materials() == null || project.tasks() == null
                    || project.leaders() == null || project.activity() == null) {
                throw new IllegalArgumentException("Invalid project data.");
            }
            for (var entry : project.materials().entrySet()) {
                var material = entry.getValue();
                if (material == null || material.itemId() == null || !entry.getKey().equals(material.itemId())
                        || material.requested() < 0 || material.stored() < 0) throw new IllegalArgumentException("Invalid material data.");
            }
            for (var task : project.tasks()) {
                if (task == null || task.id() == null || task.title() == null || task.helpers() == null) throw new IllegalArgumentException("Invalid task data.");
            }
            for (var activity : project.activity()) {
                if (activity == null || activity.action() == null) throw new IllegalArgumentException("Invalid activity data.");
            }
        }
        for (var suggestion : state.suggestions()) {
            if (suggestion == null || suggestion.id() == null || suggestion.title() == null
                    || suggestion.description() == null || suggestion.supporters() == null) throw new IllegalArgumentException("Invalid suggestion data.");
        }
        return state;
    }

    public static String forClient(BoardState state) {
        JsonObject snapshot = GSON.toJsonTree(state).getAsJsonObject();
        for (var element : snapshot.getAsJsonArray("projects")) {
            JsonObject project = element.getAsJsonObject();
            JsonArray activity = project.getAsJsonArray("activity");
            JsonArray recent = new JsonArray();
            for (int index = Math.max(0, activity.size() - 50); index < activity.size(); index++) {
                recent.add(activity.get(index));
            }
            project.add("activity", recent);
        }
        return snapshot.toString();
    }
}
