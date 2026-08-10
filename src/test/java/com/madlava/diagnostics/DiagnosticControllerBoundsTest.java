package com.madlava.diagnostics;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticControllerBoundsTest {
    @Test void utf8TruncationHonorsByteLimitWithoutSplittingSurrogatePairs() {
        String value="prefix-🔥🔥🔥-suffix";
        for (int limit=1; limit<value.getBytes(StandardCharsets.UTF_8).length; limit++) {
            byte[] truncated=DiagnosticController.truncateUtf8(value,limit);
            assertTrue(truncated.length<=limit);
            String decoded=new String(truncated,StandardCharsets.UTF_8);
            assertFalse(decoded.contains("\uFFFD"));
        }
    }

    @Test void configurationReloadDoesNotClaimSuccessWhenRuntimeControlIsNotWired() {
        DiagnosticController controller=new DiagnosticController(
                Path.of("."),1,1024,1024,1,Duration.ZERO,Duration.ZERO);
        assertEquals("UNAVAILABLE",controller.reloadConfiguration("{\"enabled\":false}"));
        assertEquals("{}",controller.getEffectiveConfiguration());
        assertEquals("REJECTED",controller.reloadConfiguration("not-json"));
    }

    @Test void invalidDiagnosticBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new DiagnosticController(
                Path.of("."),0,1,1,1,Duration.ZERO,Duration.ZERO));
        assertThrows(IllegalArgumentException.class,()->new DiagnosticController(
                Path.of("."),1,0,1,1,Duration.ZERO,Duration.ZERO));
        assertThrows(IllegalArgumentException.class,()->new DiagnosticController(
                Path.of("."),1,2,1,1,Duration.ZERO,Duration.ZERO));
        assertThrows(IllegalArgumentException.class,()->new DiagnosticController(
                Path.of("."),1,1,1,1,Duration.ofSeconds(-1),Duration.ZERO));
    }

    @Test void freshlyCreatedDumpIsNotDeletedByZeroAgeRetention() throws Exception {
        Path root=java.nio.file.Files.createTempDirectory("madlava-diagnostics");
        DiagnosticController controller=new DiagnosticController(root,2,64*1024,128*1024,2,Duration.ZERO,Duration.ZERO);
        String created=controller.triggerThreadDump();
        assertNotEquals("FAILED",created); assertNotEquals("THROTTLED",created);
        assertTrue(java.nio.file.Files.isRegularFile(root.resolve(created)));
    }
}
