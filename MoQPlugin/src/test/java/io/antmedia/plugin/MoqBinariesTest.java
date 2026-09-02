package io.antmedia.plugin;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

public class MoqBinariesTest {

    private static final String MISSING = "moq-does-not-exist";

    @Test
    public void testScanPath() throws Exception {
        Path dir = Files.createTempDirectory("moq-bin");
        Path bin = Files.writeString(dir.resolve("fake-bin"), "");
        Files.setPosixFilePermissions(bin, PosixFilePermissions.fromString("r-x------"));
        String absolute = bin.toAbsolutePath().toString();

        assertEquals(absolute, MoqBinaries.scanPath("fake-bin", dir.toString()));

        // Empty and non-existent PATH entries are skipped rather than aborting the scan
        assertEquals(absolute, MoqBinaries.scanPath("fake-bin",
                File.pathSeparator + "/no/such/dir" + File.pathSeparator + dir));

        // A miss hands back the bare name so ProcessBuilder produces a readable
        // "cannot run program" instead of us guessing at a path
        assertEquals(MISSING, MoqBinaries.scanPath(MISSING, dir.toString()));
        assertEquals(MISSING, MoqBinaries.scanPath(MISSING, null));
        assertEquals(MISSING, MoqBinaries.scanPath(MISSING, ""));
    }

    @Test
    public void testResolveCachesBothHitsAndMisses() throws Exception {
        String missing = MISSING + System.nanoTime();
        try {
            assertEquals(missing, MoqBinaries.resolve(missing));
            assertEquals("the miss must be cached, not re-scanned on every spawn",
                    missing, cache().get(missing));

            // Cache wins over a later PATH scan
            cache().put(missing, "/opt/moq/bin/" + missing);
            assertEquals("/opt/moq/bin/" + missing, MoqBinaries.resolve(missing));

            // "sh" stands in for moq: something that really is on PATH everywhere we run
            String sh = MoqBinaries.resolve("sh");
            assertNotEquals("a binary on PATH must resolve to an absolute path", "sh", sh);
            assertTrue(sh, new File(sh).canExecute());
        } finally {
            cache().remove(missing);
            cache().remove("sh");
        }
    }

    private static Map<String, String> cache() {
        return TestReflect.staticField(MoqBinaries.class, "cache");
    }
}
