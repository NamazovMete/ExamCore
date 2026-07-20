package com.examcore.data;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseFlushTest {

    @Test
    void flush_clearsDirtyFlagAfterPersistingChanges() throws Exception {
        Database database = new Database();

        Field dirtyField = Database.class.getDeclaredField("dirty");
        dirtyField.setAccessible(true);

        assertTrue((Boolean) dirtyField.get(database), "New databases should start with pending changes");

        database.flush();

        assertFalse((Boolean) dirtyField.get(database), "Flush should clear the pending-change flag");
    }
}
