package com.clockworktown.campboard.data;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Suggestion {
    private String id;
    private String title;
    private String description;
    private UUID suggestedBy;
    private Instant createdAt = Instant.now();
    private Set<UUID> supporters = new LinkedHashSet<>();

    public Suggestion(String id, String title, String description, UUID suggestedBy) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.suggestedBy = suggestedBy;
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

    public UUID suggestedBy() {
        return suggestedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Set<UUID> supporters() {
        return supporters;
    }
}
