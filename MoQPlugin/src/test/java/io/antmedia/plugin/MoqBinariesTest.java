package io.antmedia.plugin;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
}
