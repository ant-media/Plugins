package io.antmedia.plugin;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player pages are a vite build that build.sh copies into resources. Asset names are
 * content-hashed, so dropping the old bundles without copying the new ones ships HTML that
 * 404s on its own scripts, and nothing else in the build notices.
 */
public class PlayerBundleTest {

    private static final ClassLoader CLASSPATH = PlayerBundleTest.class.getClassLoader();
    private static final String ROOT = "moq-ams-player-build/";
    private static final List<String> PAGES = List.of("index.html", "play.html", "publish.html");

    /** src/href naming a file we ship: absolute URLs, data: and anchors are somebody else's problem. */
    private static final Pattern LOCAL_REF = Pattern.compile("(?:src|href)=\"(?!\\w+:|//|/|#)\\.?/?([^\"]+)\"");

    @Test
    public void testEveryPageReferenceResolvesInTheJar() throws Exception {
        List<String> missing = new ArrayList<>();
        List<String> scripts = new ArrayList<>();

        for (String page : PAGES) {
            String html;
            try (InputStream in = CLASSPATH.getResourceAsStream(ROOT + page)) {
                assertNotNull(page + " is not on the classpath, so it will not be in the jar", in);
                html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            Matcher ref = LOCAL_REF.matcher(html);
            while (ref.find()) {
                String target = ref.group(1);
                if (CLASSPATH.getResource(ROOT + target) == null) {
                    missing.add(page + " -> " + target);
                }
                if (target.endsWith(".js")) {
                    scripts.add(target);
                }
            }
        }

        assertEquals("player pages reference files the build output does not have", List.of(), missing);
        // Stripped script tags over an empty assets/ would otherwise pass the check above
        assertFalse("the pages load no javascript at all, the vite build never landed", scripts.isEmpty());
    }
}
