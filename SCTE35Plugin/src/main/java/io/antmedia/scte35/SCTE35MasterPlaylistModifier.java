package io.antmedia.scte35;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds the bandwidth a master playlist advertises still.
 *
 * The server measures each rendition as it runs and rewrites the master every few
 * seconds, so BANDWIDTH moves on almost every request. Ad insertion services build a
 * transcode profile per rendition out of these attributes, and a rendition whose
 * bandwidth never settles is hard for them to recognise as the same one twice.
 *
 * Encoder rungs are replaced with the bitrate configured for their height. The source
 * rendition has no configured bitrate, only a measurement, so its first value is kept.
 */
public class SCTE35MasterPlaylistModifier {

    private static final String STREAM_INF = "#EXT-X-STREAM-INF:";

    private static final Pattern BANDWIDTH = Pattern.compile("(?<![A-Z-])BANDWIDTH=(\\d+)");
    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=\\d+x(\\d+)");

    private SCTE35MasterPlaylistModifier() {
        //utility class
    }

    /**
     * @param configuredBandwidths bitrate per rendition height, from the encoder settings
     * @param pinnedBandwidths     first bandwidth seen per rendition, added to as needed
     * @return the playlist with steady bandwidths, or null when nothing changed
     */
    public static String pinBandwidths(String playlist, String streamId,
            Map<Integer, Long> configuredBandwidths, Map<String, Long> pinnedBandwidths) {

        if (playlist == null || !playlist.contains(STREAM_INF)) {
            return null;
        }

        String[] lines = playlist.split("\n");
        StringBuilder modified = new StringBuilder(playlist.length());
        boolean changed = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith(STREAM_INF)) {
                long bandwidth = bandwidthFor(line, renditionAfter(lines, i), streamId,
                        configuredBandwidths, pinnedBandwidths);
                if (bandwidth > 0) {
                    String steady = BANDWIDTH.matcher(line).replaceFirst("BANDWIDTH=" + bandwidth);
                    changed |= !steady.equals(line);
                    line = steady;
                }
            }

            modified.append(line).append('\n');
        }
        return changed ? modified.toString() : null;
    }

    private static long bandwidthFor(String streamInfLine, String rendition, String streamId,
            Map<Integer, Long> configuredBandwidths, Map<String, Long> pinnedBandwidths) {

        if (rendition == null) {
            return 0;
        }

        String name = rendition.substring(rendition.lastIndexOf('/') + 1);

        //the source rendition is served under the plain stream name and can share its
        //resolution with a rung, so it must not borrow that rung's configured bitrate
        if (!name.equals(streamId + ".m3u8")) {
            Integer height = heightOf(streamInfLine);
            Long configured = height == null ? null : configuredBandwidths.get(height);
            if (configured != null) {
                return configured;
            }
        }

        Long measured = valueOf(BANDWIDTH, streamInfLine);
        if (measured == null || measured == 0) {
            return 0;
        }
        return pinnedBandwidths.computeIfAbsent(name, key -> measured);
    }

    /** The rendition a stream info line refers to sits on the next line that is not a tag. */
    private static String renditionAfter(String[] lines, int streamInfIndex) {
        for (int i = streamInfIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return line;
            }
        }
        return null;
    }

    private static Integer heightOf(String streamInfLine) {
        Long height = valueOf(RESOLUTION, streamInfLine);
        return height == null ? null : height.intValue();
    }

    private static Long valueOf(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
}
