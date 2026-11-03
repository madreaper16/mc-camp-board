package com.clockworktown.campboard.storage;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MaterialListImportTest {
    private static final Set<String> ITEMS = Set.of("minecraft:stone", "mod:panel_2", "mod:folder/block", "mod:2x_panel");

    @Test void handlesSupportedFormatsAndCombinesDuplicates() {
        var parsed = MaterialListImport.parse(List.of("\uFEFFminecraft:stone 12 # foundation", "8 stone", "# ignored", "mod:panel_2, 64", "4 - mod:folder/block", "mod:2x_panel 128"), ITEMS::contains);
        assertEquals(20, parsed.get("minecraft:stone"));
        assertEquals(64, parsed.get("mod:panel_2"));
        assertEquals(4, parsed.get("mod:folder/block"));
        assertEquals(128, parsed.get("mod:2x_panel"));
    }

    @Test void rejectsBadLinesUnknownItemsAndOverflow() {
        for (String line : List.of("stone -5", "stone 0", "stone 999999999999", "mod:unknown 4", "{\"minecraft:stone\": 64}", "stone", "")) {
            assertThrows(IllegalArgumentException.class, () -> MaterialListImport.parse(List.of(line), ITEMS::contains), line);
        }
        assertThrows(IllegalArgumentException.class, () -> MaterialListImport.parse(List.of("stone 2147483647", "stone 1"), ITEMS::contains));
    }
}
