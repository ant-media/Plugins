package io.antmedia.scte35;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the ad breaks detected on one stream.
 *
 * A break is placed on the playlist timeline rather than on segment numbers, because
 * every HLS rendition numbers its segments independently. That timeline is the one
 * EXT-X-PROGRAM-DATE-TIME reports, and it is not the server clock: it advances with the
 * media, so an ingest that runs faster or slower than realtime drifts away from wall
 * clock. Breaks are therefore anchored to the live edge of a playlist as it is served,
 * and the anchor is frozen so the marker never moves once a player has seen it.
 *
 * The anchor is taken from whichever rendition is served first. All renditions of a
 * stream share the same program date time, so one anchor is valid for all of them.
 */
public class SCTE35CueTracker {

    private static final Logger logger = LoggerFactory.getLogger(SCTE35CueTracker.class);

    /**
     * Only recent breaks can still be inside a playlist window, older ones are dropped.
     * Visibility is decided per playlist, this only bounds memory.
     */
    private static final int MAX_TRACKED_CUES = 16;

    public static final long NOT_ANCHORED = -1;

    public static class Cue {

        private final int eventId;
        private final long durationMs;

        private volatile long outTimeMs = NOT_ANCHORED;
        private volatile long inTimeMs = NOT_ANCHORED;
        private volatile boolean cueInSignalled;

        Cue(int eventId, long durationMs) {
            this.eventId = eventId;
            this.durationMs = durationMs;
        }

        public int getEventId() {
            return eventId;
        }

        /**
         * Break duration in milliseconds, or -1 when the signal did not carry one.
         */
        public long getDurationMs() {
            return durationMs;
        }

        /**
         * Where the break opens on the playlist timeline, or NOT_ANCHORED before the
         * first playlist has been served.
         */
        public long getOutTimeMs() {
            return outTimeMs;
        }

        /**
         * Where the break ends on the playlist timeline, or -1 while it is still open ended.
         *
         * A break never outlives the duration it announced, otherwise the Elapsed we
         * report climbs above the Duration we put in the same tag.
         */
        public long getEndTimeMs() {
            long announcedEnd = outTimeMs != NOT_ANCHORED && durationMs > 0 ? outTimeMs + durationMs : -1;
            if (inTimeMs == NOT_ANCHORED) {
                return announcedEnd;
            }
            return announcedEnd == -1 ? inTimeMs : Math.min(inTimeMs, announcedEnd);
        }

        public boolean isAnchored() {
            return outTimeMs != NOT_ANCHORED;
        }

        boolean isOpen() {
            return !cueInSignalled;
        }
    }

    private final List<Cue> cues = new CopyOnWriteArrayList<>();
    private final String streamId;

    public SCTE35CueTracker(String streamId) {
        this.streamId = streamId;
    }

    /**
     * A splice insert is signalled repeatedly before the splice point, so a break that
     * is already open is left alone. A break under a new id supersedes whatever was
     * running, since a stream cannot be inside two ad breaks at once.
     */
    public synchronized void cueOut(int eventId, long durationMs) {
        Cue openCue = findOpenCue();
        if (openCue != null) {
            if (openCue.getEventId() == eventId) {
                return;
            }
            logger.warn("SCTE-35 break eventId:{} on stream:{} is closed by a new break eventId:{}",
                    openCue.getEventId(), streamId, eventId);
            openCue.cueInSignalled = true;
        }

        cues.add(new Cue(eventId, durationMs));
        while (cues.size() > MAX_TRACKED_CUES) {
            cues.remove(0);
        }

        logger.info("SCTE-35 break started on stream:{} eventId:{} duration:{}ms", streamId, eventId, durationMs);
    }

    /**
     * A cue in without an event id can only refer to whatever break is running, which is
     * what a time signal has to fall back to.
     */
    public synchronized void cueIn(int eventId) {
        Cue cue = findOpenCue();
        if (cue == null || (eventId >= 0 && cue.getEventId() != eventId)) {
            logger.warn("SCTE-35 CUE-IN for eventId:{} on stream:{} has no open break", eventId, streamId);
            return;
        }

        cue.cueInSignalled = true;
        logger.info("SCTE-35 break ended on stream:{} eventId:{}", streamId, cue.getEventId());
    }

    /**
     * Pins breaks that are waiting for a position to the live edge of a playlist being
     * served. Called once per playlist request, before the tags are written.
     *
     * Renditions are usually requested in parallel, so this is synchronized to make the
     * first live edge win rather than the last one to be written.
     *
     * @param liveEdgeMs program date time the next segment of that playlist will carry
     */
    public synchronized void anchorPendingCues(long liveEdgeMs) {
        if (liveEdgeMs == NOT_ANCHORED) {
            return;
        }

        for (Cue cue : cues) {
            if (cue.outTimeMs == NOT_ANCHORED) {
                cue.outTimeMs = liveEdgeMs;
                logger.info("SCTE-35 break eventId:{} on stream:{} opens at {}", cue.getEventId(), streamId, liveEdgeMs);
            }
            else if (cue.cueInSignalled && cue.inTimeMs == NOT_ANCHORED && liveEdgeMs > cue.outTimeMs) {
                cue.inTimeMs = liveEdgeMs;
                logger.info("SCTE-35 break eventId:{} on stream:{} closes at {}", cue.getEventId(), streamId, liveEdgeMs);
            }
        }
    }

    public List<Cue> getCues() {
        return cues;
    }

    /** Most recent break that has not been closed yet, whatever its id. */
    private Cue findOpenCue() {
        for (int i = cues.size() - 1; i >= 0; i--) {
            Cue cue = cues.get(i);
            if (cue.isOpen()) {
                return cue;
            }
        }
        return null;
    }
}
