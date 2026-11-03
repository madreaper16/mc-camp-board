package com.clockworktown.campboard.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardTransferTest {
    @Test void reassemblesLargeUnicodeSnapshotWithoutBreakingSurrogates() {
        String json = "a".repeat(8191) + "\uD83D\uDE00" + "b".repeat(80_000);
        var parts = BoardTransfer.split(json);
        BoardTransfer transfer = new BoardTransfer();
        BoardTransfer.Result result = null;
        for (int index = 0; index < parts.size(); index++) {
            assertTrue(parts.get(index).length() <= BoardTransfer.CHUNK_SIZE);
            assertFalse(Character.isHighSurrogate(parts.get(index).charAt(parts.get(index).length() - 1)));
            result = transfer.accept("board_a", "transfer", true, index, parts.size(), parts.get(index), 1);
        }
        assertNotNull(result);
        assertEquals(json, result.json());
        assertTrue(result.opening());
    }

    @Test void rejectsOutOfOrderMixedExpiredAndOversizedTransfers() {
        BoardTransfer transfer = new BoardTransfer();
        assertThrows(IllegalArgumentException.class, () -> transfer.accept("a", "t", true, 1, 2, "x", 1));
        transfer.accept("a", "t", false, 0, 2, "x", 1);
        assertThrows(IllegalArgumentException.class, () -> transfer.accept("b", "t", false, 1, 2, "y", 2));
        transfer.accept("a", "t", false, 0, 2, "x", 1);
        assertThrows(IllegalArgumentException.class, () -> transfer.accept("a", "t", false, 1, 2, "y", 30_002));
        assertThrows(IllegalArgumentException.class, () -> transfer.accept("a", "t", false, 0, 1025, "x", 1));
        assertThrows(IllegalArgumentException.class, () -> BoardTransfer.split("x".repeat(BoardTransfer.CHUNK_SIZE * BoardTransfer.MAX_CHUNKS + 1)));
    }

    @Test void ordinaryRefreshDoesNotRequestScreenOpening() {
        var result = new BoardTransfer().accept("a", "t", false, 0, 1, "{}", 0);
        assertFalse(result.opening());
    }
}
