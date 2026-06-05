package com.clockworktown.campboard.data;

import java.time.Instant;
import java.util.UUID;

public record ActivityEntry(Instant time, UUID actor, String action) {
    public static ActivityEntry now(UUID actor, String action) {
        return new ActivityEntry(Instant.now(), actor, action);
    }
}
