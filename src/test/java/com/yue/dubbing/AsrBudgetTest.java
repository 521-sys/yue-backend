package com.yue.dubbing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class AsrBudgetTest {
    @TempDir Path directory;
    @Test void persistsReservationsAndRejectsOverBudgetAfterRestart() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var file = directory.resolve("budget.json");
        new AsrBudget(file, 600, clock).reserve(300);
        new AsrBudget(file, 600, clock).reserve(300);
        assertThrows(IllegalStateException.class, () -> new AsrBudget(file, 600, clock).reserve(1));
        var nextMonth = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        assertDoesNotThrow(() -> new AsrBudget(file, 600, nextMonth).reserve(300));
    }
    @Test void corruptedBudgetFailsClosed() throws Exception {
        var file = directory.resolve("budget.json");
        java.nio.file.Files.writeString(file, "broken");
        assertThrows(Exception.class, () -> new AsrBudget(file, 600, Clock.systemUTC()).reserve(20));
    }
}
