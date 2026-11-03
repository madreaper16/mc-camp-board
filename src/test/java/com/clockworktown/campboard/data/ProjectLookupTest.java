package com.clockworktown.campboard.data;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLookupTest {
    private BoardState board(String id) {
        BoardState state = new BoardState();
        state.projects().add(new Project(id, "House", "", UUID.randomUUID()));
        return state;
    }

    @Test void findsPhysicalBoardWithoutPrioritizingLegacyData() {
        var result = ProjectLookup.unique(Map.of("", board("other"), "board_a", board("house")), "house").orElseThrow();
        assertEquals("board_a", result.boardId());
        assertEquals("house", result.project().id());
    }

    @Test void rejectsDuplicateIdsAcrossBoards() {
        assertThrows(IllegalArgumentException.class, () -> ProjectLookup.unique(Map.of("", board("house"), "board_a", board("house")), "house"));
    }

    @Test void missingIdDoesNotChooseAnotherProject() {
        assertTrue(ProjectLookup.unique(Map.of("board_a", board("house")), "missing").isEmpty());
    }
}
