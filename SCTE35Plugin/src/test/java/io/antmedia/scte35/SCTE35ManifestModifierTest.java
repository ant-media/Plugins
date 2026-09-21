package io.antmedia.scte35;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import io.antmedia.scte35.SCTE35CueTracker.Cue;

public class SCTE35ManifestModifierTest {

    private static final long START = 1787690000000L;
    private static final long MINUTE = 60000L;

    @Test
    public void testCueOutLandsOnFirstSegmentAtOrAfterTheBreak() {
        // segments at +0s +2s +4s +6s +8s, break opens at +5s
        String playlist = playlist("stream", 10, 2.0, START, 5);
        List<Cue> cues = breakAt(START + 5000, MINUTE);

        String modified = SCTE35ManifestModifier.injectSCTE35Tags(playlist, cues);

        assertNotNull(modified);
        assertEquals(START + 6000, timeOfSegmentAfter(modified, "#EXT-X-CUE-OUT:60.000"));
    }

    @Test
    public void testRenditionsWithDifferentNumberingMarkTheSameInstant() {
        List<Cue> cues = breakAt(START + 20000, MINUTE);

        // same wall clock, different media sequence and different segment duration
        String source = SCTE35ManifestModifier.injectSCTE35Tags(playlist("stream", 10, 2.0, START, 20), cues);
        String rung = SCTE35ManifestModifier.injectSCTE35Tags(playlist("stream_480p1800kbps", 208, 2.4, START, 20), cues);

        long sourceMark = timeOfSegmentAfter(source, "#EXT-X-CUE-OUT:60.000");
        long rungMark = timeOfSegmentAfter(rung, "#EXT-X-CUE-OUT:60.000");

        assertTrue("renditions marked the break more than one segment apart",
                Math.abs(sourceMark - rungMark) <= 2400);
    }

    @Test
    public void testBreakThatAlreadyEndedIsNotSignalled() {
        // window sits well past a break that was never closed by a CUE-IN
        String playlist = playlist("stream", 300, 2.0, START + 5 * MINUTE, 15);
        List<Cue> cues = breakAt(START, MINUTE);

        String modified = SCTE35ManifestModifier.injectSCTE35Tags(playlist, cues);

        assertNull("a break that ended long ago must not be signalled at all", modified);
    }

    @Test
    public void testElapsedStaysBelowDurationInsideTheBreak() {
        String playlist = playlist("stream", 10, 2.0, START, 15);
        List<Cue> cues = breakAt(START - 10000, MINUTE);

        String modified = SCTE35ManifestModifier.injectSCTE35Tags(playlist, cues);

        assertNotNull(modified);
        Matcher matcher = Pattern.compile("Elapsed=([0-9.]+),Duration=([0-9.]+)").matcher(modified);
        int found = 0;
        while (matcher.find()) {
            double elapsed = Double.parseDouble(matcher.group(1));
            double duration = Double.parseDouble(matcher.group(2));
            assertTrue("Elapsed " + elapsed + " is past Duration " + duration, elapsed < duration);
            found++;
        }
        assertTrue(found > 0);
    }

    @Test
    public void testBreakStartedBeforeTheWindowHasNoCueOut() {
        String playlist = playlist("stream", 100, 2.0, START + 30000, 15);
        List<Cue> cues = breakAt(START, MINUTE);

        String modified = SCTE35ManifestModifier.injectSCTE35Tags(playlist, cues);

        assertNotNull(modified);
        assertFalse("the break opened before this window so it cannot carry a CUE-OUT",
                modified.contains("#EXT-X-CUE-OUT:"));
        assertTrue(modified.contains("#EXT-X-CUE-OUT-CONT:"));
    }

    @Test
    public void testCueInClosesTheBreak() {
        String playlist = playlist("stream", 10, 2.0, START, 15);
        List<Cue> cues = closedBreak(START + 4000, START + 14000, MINUTE);

        String modified = SCTE35ManifestModifier.injectSCTE35Tags(playlist, cues);

        assertNotNull(modified);
        assertEquals(START + 4000, timeOfSegmentAfter(modified, "#EXT-X-CUE-OUT:60.000"));
        assertEquals(START + 14000, timeOfSegmentAfter(modified, "#EXT-X-CUE-IN"));
        assertFalse("no continuation tag belongs after the break closed",
                modified.substring(modified.indexOf("#EXT-X-CUE-IN")).contains("#EXT-X-CUE-OUT-CONT"));
    }

    @Test
    public void testPlaylistWithoutProgramDateTimeIsLeftAlone() {
        String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:2.000000,\nstream000000000.ts\n";

        assertEquals(SCTE35CueTracker.NOT_ANCHORED, SCTE35ManifestModifier.liveEdgeTime(playlist));
        assertNull(SCTE35ManifestModifier.injectSCTE35Tags(playlist, breakAt(START, MINUTE)));
    }

    @Test
    public void testNoCuesLeavesPlaylistAlone() {
        String playlist = playlist("stream", 10, 2.0, START, 5);

        assertNull(SCTE35ManifestModifier.injectSCTE35Tags(playlist, Collections.emptyList()));
    }

    @Test
    public void testSegmentLinesAreNotLost() {
        String playlist = playlist("stream", 10, 2.0, START, 15);

        String modified = SCTE35ManifestModifier.injectSCTE35Tags(playlist, breakAt(START + 4000, MINUTE));

        assertEquals(countOccurrences(playlist, ".ts"), countOccurrences(modified, ".ts"));
        assertEquals(countOccurrences(playlist, "#EXTINF:"), countOccurrences(modified, "#EXTINF:"));
    }

    @Test
    public void testLiveEdgeReadsTheLastSegmentWhicheverOrderItsTagsAreIn() {
        // date time before EXTINF, and a last segment longer than the ones before it
        String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n"
                + "#EXT-X-PROGRAM-DATE-TIME:" + iso(START) + "\n#EXTINF:2.000000,\nseg0.ts\n"
                + "#EXT-X-PROGRAM-DATE-TIME:" + iso(START + 2000) + "\n#EXTINF:4.000000,\nseg1.ts\n";

        assertEquals(START + 2000 + 4000, SCTE35ManifestModifier.liveEdgeTime(playlist));
    }

    @Test
    public void testNewBreakClosesTheOneStillRunning() {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(1, MINUTE);
        tracker.anchorPendingCues(START);
        tracker.cueOut(2, MINUTE);
        tracker.anchorPendingCues(START + 10000);

        assertEquals(2, tracker.getCues().size());
        assertEquals(START + 10000, tracker.getCues().get(0).getEndTimeMs());
        assertEquals(START + 10000 + MINUTE, tracker.getCues().get(1).getEndTimeMs());
    }

    @Test
    public void testCueInForADifferentBreakIsIgnored() {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(1, MINUTE);
        tracker.anchorPendingCues(START);

        tracker.cueIn(9);
        tracker.anchorPendingCues(START + 10000);

        // still closing on its own duration, not on the stray cue in
        assertEquals(START + MINUTE, tracker.getCues().get(0).getEndTimeMs());
    }

    @Test
    public void testLiveEdgeIsWhereTheNextSegmentStarts() {
        // 15 segments of 2.4s starting at START, so the next one starts at START + 36s
        String playlist = playlist("stream_480p1800kbps", 208, 2.4, START, 15);

        assertEquals(START + 36000, SCTE35ManifestModifier.liveEdgeTime(playlist));
    }

    @Test
    public void testBreakWithoutAnAnchorIsNotWritten() {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(1, MINUTE);

        assertNull(SCTE35ManifestModifier.injectSCTE35Tags(playlist("stream", 10, 2.0, START, 15), tracker.getCues()));
    }

    @Test
    public void testAnchorIsFrozenAfterTheFirstPlaylist() {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(1, MINUTE);

        tracker.anchorPendingCues(START + 10000);
        tracker.anchorPendingCues(START + 30000);

        assertEquals(START + 10000, tracker.getCues().get(0).getOutTimeMs());
    }

    @Test
    public void testRepeatedSpliceInsertOpensOneBreak() {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(7, MINUTE);
        tracker.cueOut(7, MINUTE);
        tracker.cueOut(7, MINUTE);

        assertEquals(1, tracker.getCues().size());
    }

    @Test
    public void testBreakEndsAtItsDurationWhenNoCueInArrives() {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(1, MINUTE);
        tracker.anchorPendingCues(START);

        assertEquals(START + MINUTE, tracker.getCues().get(0).getEndTimeMs());
    }

    private static List<Cue> breakAt(long outTimeMs, long durationMs) {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(1, durationMs);
        tracker.anchorPendingCues(outTimeMs);
        return tracker.getCues();
    }

    private static List<Cue> closedBreak(long outTimeMs, long inTimeMs, long durationMs) {
        SCTE35CueTracker tracker = new SCTE35CueTracker("test");
        tracker.cueOut(1, durationMs);
        tracker.anchorPendingCues(outTimeMs);
        tracker.cueIn(1);
        tracker.anchorPendingCues(inTimeMs);
        return tracker.getCues();
    }

    private static String playlist(String name, int mediaSequence, double segmentDuration, long firstSegmentMs, int count) {
        StringBuilder playlist = new StringBuilder();
        playlist.append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:")
                .append((int) Math.ceil(segmentDuration)).append('\n')
                .append("#EXT-X-MEDIA-SEQUENCE:").append(mediaSequence).append('\n');

        for (int i = 0; i < count; i++) {
            long time = firstSegmentMs + (long) (i * segmentDuration * 1000);
            playlist.append(String.format("#EXTINF:%f,%n", segmentDuration).replace(System.lineSeparator(), "\n"))
                    .append("#EXT-X-PROGRAM-DATE-TIME:").append(iso(time)).append('\n')
                    .append(String.format("%s%09d.ts%n", name, mediaSequence + i).replace(System.lineSeparator(), "\n"));
        }
        return playlist.toString();
    }

    private static String iso(long epochMillis) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx"));
    }

    /** Program date time of the segment that the given tag opens. */
    private static long timeOfSegmentAfter(String playlist, String tag) {
        int tagIndex = playlist.indexOf(tag);
        assertTrue("tag not found: " + tag, tagIndex != -1);

        int pdtIndex = playlist.indexOf("#EXT-X-PROGRAM-DATE-TIME:", tagIndex);
        assertTrue("no segment after the tag", pdtIndex != -1);

        int valueStart = pdtIndex + "#EXT-X-PROGRAM-DATE-TIME:".length();
        String value = playlist.substring(valueStart, playlist.indexOf('\n', valueStart)).trim();
        return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx"))
                .toInstant().toEpochMilli();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index != -1) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
