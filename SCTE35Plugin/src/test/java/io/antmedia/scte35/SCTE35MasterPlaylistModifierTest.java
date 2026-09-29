package io.antmedia.scte35;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

public class SCTE35MasterPlaylistModifierTest {

    private static final long RUNG_360P = 1500000L + 128000L;
    private static final long RUNG_480P = 1800000L + 128000L;

    private static final Pattern BANDWIDTH = Pattern.compile("(?<![A-Z-])BANDWIDTH=(\\d+)");

    private Map<Integer, Long> configured;
    private Map<String, Long> pinned;

    @Before
    public void setUp() {
        configured = new HashMap<>();
        configured.put(360, RUNG_360P);
        configured.put(480, RUNG_480P);
        pinned = new ConcurrentHashMap<>();
    }

    /** The source rendition shares its resolution with the 360p rung, as it does on a real ladder. */
    private static String master(long source, long rung360, long rung480) {
        return "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=" + source + ",RESOLUTION=640x360,CODECS=\"avc1.42e00a,mp4a.40.2\"\n"
                + "teststream.m3u8\n"
                + "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=" + rung360 + ",RESOLUTION=640x360,CODECS=\"avc1.42e00a,mp4a.40.2\"\n"
                + "teststream_360p1500kbps.m3u8\n"
                + "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=" + rung480 + ",RESOLUTION=854x480,CODECS=\"avc1.42e00a,mp4a.40.2\"\n"
                + "teststream_480p1800kbps.m3u8\n";
    }

    @Test
    public void testRungsGetTheConfiguredBitrate() {
        String modified = SCTE35MasterPlaylistModifier.pinBandwidths(
                master(90376, 277112, 362200), "teststream", configured, pinned);

        assertNotNull(modified);
        assertEquals(RUNG_360P, bandwidthOf(modified, "teststream_360p1500kbps.m3u8"));
        assertEquals(RUNG_480P, bandwidthOf(modified, "teststream_480p1800kbps.m3u8"));
    }

    @Test
    public void testSourceDoesNotBorrowTheRungBitrateOfTheSameResolution() {
        String modified = SCTE35MasterPlaylistModifier.pinBandwidths(
                master(90376, 277112, 362200), "teststream", configured, pinned);

        assertEquals(90376, bandwidthOf(modified, "teststream.m3u8"));
    }

    @Test
    public void testOutputDoesNotMoveWhenTheMeasurementDoes() {
        String first = SCTE35MasterPlaylistModifier.pinBandwidths(
                master(90376, 277112, 362200), "teststream", configured, pinned);
        String second = SCTE35MasterPlaylistModifier.pinBandwidths(
                master(89048, 273896, 357680), "teststream", configured, pinned);
        String third = SCTE35MasterPlaylistModifier.pinBandwidths(
                master(91176, 280200, 365600), "teststream", configured, pinned);

        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    public void testRenditionWithoutConfiguredHeightKeepsItsFirstValue() {
        configured.clear();

        //the first playlist is the one being pinned, so there is nothing to rewrite yet
        assertNull(SCTE35MasterPlaylistModifier.pinBandwidths(
                master(90376, 277112, 362200), "teststream", configured, pinned));

        String second = SCTE35MasterPlaylistModifier.pinBandwidths(
                master(11111, 22222, 33333), "teststream", configured, pinned);

        assertNotNull(second);
        assertEquals(90376, bandwidthOf(second, "teststream.m3u8"));
        assertEquals(277112, bandwidthOf(second, "teststream_360p1500kbps.m3u8"));
        assertEquals(362200, bandwidthOf(second, "teststream_480p1800kbps.m3u8"));
    }

    @Test
    public void testRenditionsAndTagsSurvive() {
        String original = master(90376, 277112, 362200);
        String modified = SCTE35MasterPlaylistModifier.pinBandwidths(original, "teststream", configured, pinned);

        for (String line : original.split("\n")) {
            if (!line.startsWith("#EXT-X-STREAM-INF")) {
                assertTrue("lost line: " + line, modified.contains(line));
            }
        }
        assertTrue(modified.contains("RESOLUTION=854x480"));
        assertTrue(modified.contains("CODECS=\"avc1.42e00a,mp4a.40.2\""));
    }

    @Test
    public void testAverageBandwidthIsNotMistakenForBandwidth() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=250000,BANDWIDTH=277112,RESOLUTION=640x360\n"
                + "teststream_360p1500kbps.m3u8\n";

        String modified = SCTE35MasterPlaylistModifier.pinBandwidths(playlist, "teststream", configured, pinned);

        assertNotNull(modified);
        assertTrue("the average was rewritten instead", modified.contains("AVERAGE-BANDWIDTH=250000"));
        assertEquals(RUNG_360P, bandwidthOf(modified, "teststream_360p1500kbps.m3u8"));
    }

    @Test
    public void testMediaPlaylistIsLeftAlone() {
        String media = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:5\n#EXTINF:2.0,\nteststream000000005.ts\n";

        assertNull(SCTE35MasterPlaylistModifier.pinBandwidths(media, "teststream", configured, pinned));
    }

    @Test
    public void testUnchangedPlaylistReturnsNull() {
        String already = master(90376, RUNG_360P, RUNG_480P);
        pinned.put("teststream.m3u8", 90376L);

        assertNull(SCTE35MasterPlaylistModifier.pinBandwidths(already, "teststream", configured, pinned));
    }

    private static long bandwidthOf(String playlist, String rendition) {
        String[] lines = playlist.split("\n");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().equals(rendition)) {
                continue;
            }
            Matcher matcher = BANDWIDTH.matcher(lines[i - 1]);
            assertTrue("no bandwidth on " + lines[i - 1], matcher.find());
            return Long.parseLong(matcher.group(1));
        }
        throw new AssertionError("rendition not found: " + rendition);
    }
}
