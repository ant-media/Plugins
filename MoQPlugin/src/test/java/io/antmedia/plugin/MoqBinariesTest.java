package io.antmedia.plugin;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MoqBinariesTest {

    private static Path makeExecutable(Path dir, String name) throws Exception {
        Path bin = dir.resolve(name);
        Files.writeString(bin, "");
        Files.setPosixFilePermissions(bin, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE));
        return bin;
    }

    @Test
    public void returnsAbsolutePathWhenFound() throws Exception {
        Path dir = Files.createTempDirectory("moq-bin");
        Path bin = makeExecutable(dir, "fake-bin");

        assertEquals(bin.toAbsolutePath().toString(),
                MoqBinaries.scanPath("fake-bin", dir.toString()));
    }

    @Test
    public void skipsEmptyAndMissingEntries() throws Exception {
        Path dir = Files.createTempDirectory("moq-bin");
        Path bin = makeExecutable(dir, "fake-bin");

        String path = "" + File.pathSeparator + "/no/such/dir" + File.pathSeparator + dir;
        assertEquals(bin.toAbsolutePath().toString(),
                MoqBinaries.scanPath("fake-bin", path));
    }

    @Test
    public void returnsBareNameWhenNotFound() {
        assertEquals("nope", MoqBinaries.scanPath("nope", "/no/such/dir"));
    }

    @Test
    public void returnsBareNameWhenPathNullOrEmpty() {
        assertEquals("nope", MoqBinaries.scanPath("nope", null));
        assertEquals("nope", MoqBinaries.scanPath("nope", ""));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> cache() throws Exception {
        Field f = MoqBinaries.class.getDeclaredField("cache");
        f.setAccessible(true);
        return (Map<String, String>) f.get(null);
    }

    @Test
    public void resolveFallsBackToBareNameAndCachesTheLookup() throws Exception {
        String name = "moq-does-not-exist-" + System.nanoTime();
        try {
            // Not on PATH: resolve hands back the bare name so ProcessBuilder produces a
            // readable "cannot run program" instead of us guessing at a path.
            assertEquals(name, MoqBinaries.resolve(name));
            assertEquals("the miss must be cached, not re-scanned on every spawn",
                    name, cache().get(name));

            // Cache wins over a later PATH scan: seed it and resolve must return the seed
            cache().put(name, "/opt/moq/bin/" + name);
            assertEquals("/opt/moq/bin/" + name, MoqBinaries.resolve(name));
        } finally {
            cache().remove(name);
        }
    }

    @Test
    public void resolveReturnsAbsolutePathForBinariesOnPath() throws Exception {
        try {
            // "sh" stands in for moq: something that really is on PATH everywhere we run
            String resolved = MoqBinaries.resolve("sh");
            assertNotEquals("a binary on PATH must resolve to an absolute path", "sh", resolved);
            assertTrue(resolved, new File(resolved).canExecute());
        } finally {
            cache().remove("sh");
        }
    }

}
