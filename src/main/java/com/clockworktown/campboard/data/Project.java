package com.clockworktown.campboard.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Project {
    public static final int MAX_DESCRIPTION_LENGTH = 250;
    public static final int MAX_LEADERS = 3;

    private String id;
    private String title;
    private String description;
    private ProjectStatus status = ProjectStatus.PLANNED;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Set<UUID> leaders = new LinkedHashSet<>();
    private Map<UUID, String> leaderNames = new LinkedHashMap<>();
    private BoardLocation primaryLocation;
    private Map<String, MaterialStack> materials = new LinkedHashMap<>();
    private List<ProjectTask> tasks = new ArrayList<>();
    private List<ActivityEntry> activity = new ArrayList<>();

    public Project(String id, String title, String description, UUID createdBy) {
        this.id = id;
        this.title = title;
        this.description = normalizeDescription(description);
        this.createdBy = createdBy;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public ProjectStatus status() {
        return status;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Set<UUID> leaders() {
        return leaders;
    }

    public Map<UUID, String> leaderNames() {
        if (leaderNames == null) {
            leaderNames = new LinkedHashMap<>();
        }
        return leaderNames;
    }

    public BoardLocation primaryLocation() {
        return primaryLocation;
    }

    public Map<String, MaterialStack> materials() {
        return materials;
    }

    public List<ProjectTask> tasks() {
        return tasks;
    }

    public List<ActivityEntry> activity() {
        return activity;
    }

    public void setDescription(String description) {
        this.description = normalizeDescription(description);
        touch();
    }

    public void setTitle(String title) {
        this.title = title == null || title.isBlank() ? this.title : title.trim();
        touch();
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
        touch();
    }

    public void setPrimaryLocation(BoardLocation primaryLocation) {
        this.primaryLocation = primaryLocation;
        touch();
    }

    public boolean addLeader(UUID leaderId) {
        if (leaders.size() >= MAX_LEADERS) {
            return false;
        }

        boolean added = leaders.add(leaderId);
        if (added) {
            touch();
        }
        return added;
    }

    public boolean addLeader(UUID leaderId, String name) {
        boolean added = addLeader(leaderId);
        if (added || leaders.contains(leaderId)) {
            leaderNames().put(leaderId, name);
        }
        return added;
    }

    public boolean addLeader(UUID leaderId, String name, int maxLeaders) {
        if (leaders.size() >= Math.max(1, Math.min(MAX_LEADERS, maxLeaders)) && !leaders.contains(leaderId)) {
            return false;
        }

        boolean added = leaders.add(leaderId);
        if (added || leaders.contains(leaderId)) {
            leaderNames().put(leaderId, name);
            touch();
        }
        return added;
    }

    public void removeLeader(UUID leaderId) {
        if (leaders.remove(leaderId)) {
            leaderNames().remove(leaderId);
            touch();
        }
    }

    public void log(UUID actor, String action) {
        activity.add(ActivityEntry.now(actor, action));
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static String normalizeDescription(String description) {
        String value = description == null ? "" : description.trim();
        if (value.length() <= MAX_DESCRIPTION_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}
