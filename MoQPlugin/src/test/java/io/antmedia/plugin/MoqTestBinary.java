package io.antmedia.plugin;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A stand-in for the real moq binary, so spawn paths can be tested for what they exec
 * rather than only for "an IOException came back".
 */
public final class MoqTestBinary {

    private MoqTestBinary() { }

    /** Writes an executable fake {@code moq} into {@code dir} that records each argument into {@code argvSink}. */
    public static Path write(Path dir, Path argvSink) throws IOException {
        Path bin = dir.resolve("moq");
        Files.writeString(bin, String.join("\n",
                "#!/bin/sh",
                "for a in \"$@\"; do echo \"$a\" >> " + argvSink + "; done",
                "exit 0",
                ""));
        Files.setPosixFilePermissions(bin, PosixFilePermissions.fromString("rwx------"));
        return bin;
    }

    /** Runs {@code body} with {@code MoqBinaries.resolve("moq")} pinned to {@code bin}, then restores the cache. */
    public static <T> T withResolvedMoq(Path bin, Callable<T> body) throws Exception {
        Field f = MoqBinaries.class.getDeclaredField("cache");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> cache = (Map<String, String>) f.get(null);
        String saved = cache.get("moq");
        cache.put("moq", bin.toString());
        try {
            return body.call();
        } finally {
            if (saved != null) {
                cache.put("moq", saved);
            } else {
                cache.remove("moq");
            }
        }
    }
}
