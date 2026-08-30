package io.antmedia.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MoqBinaries {

    /** moq-cli binds [::]:0 by default, which picks the AAAA record and dies on hosts without an IPv6 route. */
    public static final String CLIENT_BIND = "0.0.0.0:0";

    private static final Logger logger = LoggerFactory.getLogger(MoqBinaries.class);
    private static final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    private MoqBinaries() { }

    public static String resolve(String binaryName) {
        return cache.computeIfAbsent(binaryName, name -> {
            String resolved = scanPath(name, System.getenv("PATH"));
            if (resolved.equals(name)) {
                logger.warn("{} not found in PATH; ProcessBuilder.start() will fail", name);
            } else {
                logger.info("Resolved {} to {}", name, resolved);
            }
            return resolved;
        });
    }

    static String scanPath(String binaryName, String path) {
        if (path == null || path.isEmpty()) return binaryName;
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) continue;
            File f = new File(dir, binaryName);
            if (f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return binaryName;
    }
}
