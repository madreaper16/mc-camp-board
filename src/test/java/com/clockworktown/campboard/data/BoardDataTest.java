package com.clockworktown.campboard.data;

import com.clockworktown.campboard.storage.BoardJson;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BoardDataTest {
    @Test void reducedRequestsKeepDepositsAvailableForWithdrawal() {
        MaterialStack material = new MaterialStack("minecraft:stone", 128);
        material.contribute(64);
        material.adjustRequested(0);
        assertEquals(64, material.stored());
        assertEquals(0, material.remaining());
        assertEquals(64, material.withdraw(100));
        assertEquals(0, material.stored());
    }

    @Test void clientSnapshotLimitsHistoryWithoutChangingSavedData() {
        BoardState state = new BoardState();
        UUID player = UUID.randomUUID();
        Project project = new Project("house", "House", "", player);
        state.projects().add(project);
        for (int index = 0; index < 500; index++) project.log(player, "Action " + index);
        BoardState snapshot = BoardJson.fromJson(BoardJson.forClient(state));
        assertEquals(50, snapshot.projects().getFirst().activity().size());
        assertEquals("Action 499", snapshot.projects().getFirst().activity().getLast().action());
        assertEquals(500, BoardJson.fromJson(BoardJson.toJson(state)).projects().getFirst().activity().size());
    }

    @Test void invalidDataFailsInsteadOfBecomingAnEmptyBoard() {
        assertThrows(RuntimeException.class, () -> BoardJson.fromJson("null"));
        assertThrows(RuntimeException.class, () -> BoardJson.fromJson("{\"schemaVersion\":99}"));
        assertThrows(RuntimeException.class, () -> BoardJson.fromJson("{\"projects\":null}"));
        BoardState state = new BoardState();
        Project project = new Project("house", "House", "", UUID.randomUUID());
        state.projects().add(project);
        var damaged = com.google.gson.JsonParser.parseString(BoardJson.toJson(state)).getAsJsonObject();
        damaged.getAsJsonArray("projects").get(0).getAsJsonObject().getAsJsonObject("materials")
                .add("minecraft:stone", com.google.gson.JsonNull.INSTANCE);
        assertThrows(RuntimeException.class, () -> BoardJson.fromJson(damaged.toString()));
    }

    @Test void editingUsesRealIdentityAndPreservesLegacyAccountNames() {
        UUID owner = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        Project project = new Project("house", "House", "", owner);
        assertTrue(project.canEdit(owner, "Owner"));
        project.addLeader(leader, "Nickname", 3);
        assertTrue(project.canEdit(leader, "ActualAccount"));
        assertFalse(project.canEdit(UUID.randomUUID(), "Nickname"));
        project.addLeader(Project.displayLeaderId("OldAccount"), "OldAccount", 3);
        assertTrue(project.canEdit(UUID.randomUUID(), "oldaccount"));
        project.addLeader(Project.displayLeaderId("Arbitrary Alias"), "Arbitrary Alias", 3);
        assertFalse(project.canEdit(UUID.randomUUID(), "SomeOtherAccount"));
    }
}
