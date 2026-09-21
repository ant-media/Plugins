package io.antmedia.scte35;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.scte35.SCTE35CueTracker.Cue;

/**
 * Writes CUE-OUT / CUE-OUT-CONT / CUE-IN into an HLS playlist as it is served.
 *
 * Each break is placed by wall clock, against the EXT-X-PROGRAM-DATE-TIME the playlist
 * carries on its own segments. That keeps every rendition marking the same instant even
 * though renditions number their segments independently and can use different segment
 * durations.
 */
public class SCTE35ManifestModifier {

    private static final Logger logger = LoggerFactory.getLogger(SCTE35ManifestModifier.class);

    private static final String PROGRAM_DATE_TIME = "#EXT-X-PROGRAM-DATE-TIME:";
    private static final String EXTINF = "#EXTINF:";
    private static final String DISCONTINUITY = "#EXT-X-DISCONTINUITY\n";
    private static final String CUE_IN = "#EXT-X-CUE-IN\n";

    private static final long UNKNOWN_TIME = Long.MIN_VALUE;

    /** ffmpeg writes the offset as +0000, other writers use +00:00 or Z. */
    private static final DateTimeFormatter PROGRAM_DATE_TIME_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendPattern("[XXX][XX][X]")
            .toFormatter();

    private SCTE35ManifestModifier() {
        //utility class
    }

    /**
     * Program date time the next segment of this playlist will carry, which is where a
     * newly signalled break opens. Returns SCTE35CueTracker.NOT_ANCHORED when the
     * playlist carries no program date time to read.
     */
    public static long liveEdgeTime(String playlist) {
        if (playlist == null || playlist.isEmpty()) {
            return SCTE35CueTracker.NOT_ANCHORED;
        }

        long lastSegmentTime = UNKNOWN_TIME;
        double lastSegmentDuration = 0;
        long pendingTime = UNKNOWN_TIME;
        double pendingDuration = 0;

        //the tags of a segment can come in either order, so they are only claimed by its uri
        for (String line : playlist.split("\n")) {
            if (line.startsWith(EXTINF)) {
                pendingDuration = parseDuration(line.substring(EXTINF.length()));
            }
            else if (line.startsWith(PROGRAM_DATE_TIME)) {
                pendingTime = parseProgramDateTime(line.substring(PROGRAM_DATE_TIME.length()).trim());
            }
            else if (isSegmentUri(line)) {
                if (pendingTime != UNKNOWN_TIME) {
                    lastSegmentTime = pendingTime;
                    lastSegmentDuration = pendingDuration;
                }
                pendingTime = UNKNOWN_TIME;
                pendingDuration = 0;
            }
        }

        if (lastSegmentTime == UNKNOWN_TIME) {
            return SCTE35CueTracker.NOT_ANCHORED;
        }
        return lastSegmentTime + Math.round(lastSegmentDuration * 1000);
    }

    /**
     * @return the playlist with ad break tags added, or null when there is nothing to add
     */
    public static String injectSCTE35Tags(String playlist, List<Cue> cues) {
        if (playlist == null || playlist.isEmpty() || cues.isEmpty()) {
            return null;
        }

        String[] lines = playlist.split("\n");
        String[] tags = buildTags(readSegmentTimes(lines), cues);
        if (tags == null) {
            return null;
        }

        StringBuilder modified = new StringBuilder(playlist.length() + 256);
        int segment = 0;
        for (String line : lines) {
            //the tags belong in front of the segment they open, which starts at its EXTINF
            if (line.startsWith(EXTINF) && segment < tags.length && tags[segment] != null) {
                modified.append(tags[segment]);
            }

            modified.append(line).append('\n');

            if (isSegmentUri(line)) {
                segment++;
            }
        }
        return modified.toString();
    }

    /**
     * Program date time of every segment in the playlist, in order.
     * Segments without one are marked unknown and are never used as an anchor.
     */
    private static long[] readSegmentTimes(String[] lines) {
        long[] times = new long[countSegments(lines)];
        long pending = UNKNOWN_TIME;
        int segment = 0;

        for (String line : lines) {
            if (line.startsWith(PROGRAM_DATE_TIME)) {
                pending = parseProgramDateTime(line.substring(PROGRAM_DATE_TIME.length()).trim());
            }
            else if (isSegmentUri(line)) {
                times[segment] = pending;
                pending = UNKNOWN_TIME;
                segment++;
            }
        }
        return times;
    }

    private static String[] buildTags(long[] segmentTimes, List<Cue> cues) {
        String[] tags = new String[segmentTimes.length];
        boolean injected = false;

        for (Cue cue : cues) {
            if (!cue.isAnchored()) {
                continue;
            }

            long outTime = cue.getOutTimeMs();
            long endTime = cue.getEndTimeMs();

            int outSegment = segmentAt(segmentTimes, outTime);
            int inSegment = endTime > 0 ? segmentAt(segmentTimes, endTime) : -1;

            for (int i = 0; i < segmentTimes.length; i++) {
                String tag;
                if (i == outSegment) {
                    tag = DISCONTINUITY + cueOutTag(cue);
                }
                else if (i == inSegment) {
                    tag = DISCONTINUITY + CUE_IN;
                }
                else if (isInsideBreak(segmentTimes[i], outTime, endTime)) {
                    tag = cueOutContTag(cue, segmentTimes[i]);
                }
                else {
                    continue;
                }

                tags[i] = tags[i] == null ? tag : tags[i] + tag;
                injected = true;
            }
        }
        return injected ? tags : null;
    }

    /**
     * First segment that starts at or after the given instant.
     *
     * Returns -1 when the instant sits outside this playlist window, either because the
     * window already scrolled past it or because it has not been reached yet. In both
     * cases the playlist has no segment to hang the marker on.
     */
    private static int segmentAt(long[] segmentTimes, long timeMs) {
        int first = -1;
        for (int i = 0; i < segmentTimes.length; i++) {
            if (segmentTimes[i] == UNKNOWN_TIME) {
                continue;
            }
            if (first == -1) {
                first = i;
                if (timeMs < segmentTimes[i]) {
                    return -1;
                }
            }
            if (segmentTimes[i] >= timeMs) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isInsideBreak(long segmentTimeMs, long outTimeMs, long endTimeMs) {
        return segmentTimeMs != UNKNOWN_TIME
                && segmentTimeMs > outTimeMs
                && (endTimeMs <= 0 || segmentTimeMs < endTimeMs);
    }

    private static String cueOutTag(Cue cue) {
        if (cue.getDurationMs() <= 0) {
            return "#EXT-X-CUE-OUT\n";
        }
        return String.format(Locale.US, "#EXT-X-CUE-OUT:%.3f\n", cue.getDurationMs() / 1000.0);
    }

    private static String cueOutContTag(Cue cue, long segmentTimeMs) {
        double elapsed = (segmentTimeMs - cue.getOutTimeMs()) / 1000.0;
        if (cue.getDurationMs() <= 0) {
            return String.format(Locale.US, "#EXT-X-CUE-OUT-CONT:Elapsed=%.3f\n", elapsed);
        }
        return String.format(Locale.US, "#EXT-X-CUE-OUT-CONT:Elapsed=%.3f,Duration=%.3f\n",
                elapsed, cue.getDurationMs() / 1000.0);
    }

    /** EXTINF carries the duration followed by a comma and an optional title. */
    private static double parseDuration(String value) {
        try {
            int comma = value.indexOf(',');
            return Double.parseDouble((comma == -1 ? value : value.substring(0, comma)).trim());
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseProgramDateTime(String value) {
        try {
            return OffsetDateTime.parse(value, PROGRAM_DATE_TIME_FORMAT).toInstant().toEpochMilli();
        }
        catch (Exception e) {
            logger.debug("Cannot parse program date time: {}", value);
            return UNKNOWN_TIME;
        }
    }

    private static int countSegments(String[] lines) {
        int count = 0;
        for (String line : lines) {
            if (isSegmentUri(line)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isSegmentUri(String line) {
        String trimmed = line.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith("#");
    }
}
