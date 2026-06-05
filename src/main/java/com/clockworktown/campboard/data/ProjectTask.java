package com.clockworktown.campboard.data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class ProjectTask {
    private String id;
    private String title;
    private boolean completed;
    private Set<UUID> helpers = new LinkedHashSet<>();

    public ProjectTask(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public boolean completed() {
        return completed;
    }

    public Set<UUID> helpers() {
        return helpers;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void addHelper(UUID playerId) {
        helpers.add(playerId);
    }

    public void removeHelper(UUID playerId) {
        helpers.remove(playerId);
    }
}
