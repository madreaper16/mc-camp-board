package com.clockworktown.campboard.data;

import com.clockworktown.campboard.config.CampBoardConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @Test void decodedSettingsAreBoundedAndIndependentCopies() {
        var config = CampBoardConfig.fromJson("{\"maxProjectLeaders\":99,\"maxDescriptionLength\":-1,\"backupRetention\":-2,\"projectCreation\":\"invalid\"}");
        assertEquals(3, config.maxProjectLeaders());
        assertEquals(50, config.maxDescriptionLength());
        assertEquals(1, config.backupRetention());
        assertEquals("ADMIN_ONLY", config.projectCreation());
        var copy = CampBoardConfig.fromJson(config.toJson());
        copy.setBreakable(false);
        assertTrue(config.breakable());
        assertFalse(copy.breakable());
    }
}
