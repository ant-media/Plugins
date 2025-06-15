package io.antmedia.scte35;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.HttpMethod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingResponseWrapper;

import io.antmedia.filter.AbstractFilter;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.scte35.SCTE35HLSMuxer.SCTE35CueState;

/**
 * Servlet filter that injects SCTE‐35 HLS tags on the fly while serving M3U8 playlists.
 * <p>
 * It follows the same interception pattern as {@link io.antmedia.filter.HlsManifestModifierFilter}
 * but it augments the playlist with ad‐break signalling that is produced by {@link SCTE35HLSMuxer}.
 * <p>
 * Advantages:
 * <ul>
 *   <li>No race condition with FFmpeg – the original file stays untouched.</li>
 *   <li>Always serves the most recent CUE‐OUT/CUE‐IN/CUE‐OUT‐CONT state.</li>
 * </ul>
 */
public class SCTE35ManifestModifierFilter extends AbstractFilter {

    private static final Logger logger = LoggerFactory.getLogger(SCTE35ManifestModifierFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        logger.info("doFilter for HLS Manifest of SCTE-35 Plugin: request: {}", httpRequest.getRequestURI());
        // Only intercept GET requests for single‐bitrate playlists (skip master/adaptive)
        if (HttpMethod.GET.equals(httpRequest.getMethod()) && httpRequest.getRequestURI().endsWith(".m3u8") &&
                !httpRequest.getRequestURI().contains(MuxAdaptor.ADAPTIVE_SUFFIX)) {

            ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(httpResponse);
            chain.doFilter(request, wrapper);

            int status = wrapper.getStatus();
            if (status >= HttpServletResponse.SC_OK && status <= HttpServletResponse.SC_BAD_REQUEST) {
                String original = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

                String streamId = extractStreamId(httpRequest.getRequestURI());
                String modified = injectScte35Tags(original, streamId);

                wrapper.resetBuffer();
                wrapper.getOutputStream().write(modified.getBytes(StandardCharsets.UTF_8));
            }
            wrapper.copyBodyToResponse();
        }
        else {
            chain.doFilter(request, response);
        }
    }

    private String extractStreamId(String uri) {
        // Assumes URI ends with /<streamId>.m3u8
        String fileName = uri.substring(uri.lastIndexOf('/') + 1);
        return fileName.replace(".m3u8", "");
    }

    private String injectScte35Tags(String playlist, String streamId) {
        try {
            logger.info("injectScte35Tags: playlist: {}, streamId: {}", playlist, streamId);
            var app = getAntMediaApplicationAdapter();
            if (app == null) {
                return playlist;
            }
            var muxAdaptor = app.getMuxAdaptor(streamId);
            if (muxAdaptor == null) {
                logger.warn("injectScte35Tags: muxAdaptor is null for stream: {}", streamId);
                return playlist;
            }

            SCTE35HLSMuxer scte35Muxer = null;
            for (var muxer : muxAdaptor.getMuxerList()) {
                if (muxer instanceof SCTE35HLSMuxer) {
                    scte35Muxer = (SCTE35HLSMuxer) muxer;
                    break;
                }
            }

            if (scte35Muxer == null || !scte35Muxer.isSCTE35Enabled()) {
                logger.warn("injectScte35Tags: scte35Muxer is null or not enabled for stream: {}", streamId);
                return playlist;
            }

            Map<Integer, SCTE35CueState> cueMap = scte35Muxer.getActiveCues();
            if (cueMap == null || cueMap.isEmpty()) {
                logger.warn("injectScte35Tags: cueMap is null or empty for stream: {}", streamId);
                return playlist;
            }

            // Use platform-independent line separator matcher (CR, LF, CRLF)
            String[] lines = playlist.split("\\R");
            StringBuilder newContent = new StringBuilder();
            // Track if we've already emitted the opening CUE-OUT tag for the current ad break so that
            // it is not duplicated for every media segment.
            boolean cueOutInjected = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                logger.info("injectScte35Tags: line: {}", line);
                if (line.startsWith("#EXTINF:")) {
                    logger.info("injectScte35Tags: line starts with #EXTINF:");
                    // walk forward until we hit a non-comment line or end-of-file
                    int j = i + 1;
                    while (j < lines.length && lines[j].startsWith("#")) {
                        j++;
                    }
                    logger.info("THE SELECTED LINE IS: {}", lines[j]);
                    logger.info("THE SELECTED LINE ENDS WITH: {}", lines[j].endsWith(".ts"));
                    if ((lines[j].endsWith(".ts") || lines[j].endsWith(".fmp4"))) {
                        logger.info("injectScte35Tags: line ends with .ts or .fmp4:");
                        // j now points to the URI line
                        // inject tags **before** lines[j]
                        long nowMs = System.currentTimeMillis();
                        logger.info("injectScte35Tags: nowMs: {}", nowMs);
                        for (SCTE35CueState cue : cueMap.values()) {
                            double elapsedSec = (nowMs - cue.wallClockStartMs) / 1000.0;
                            logger.info("injectScte35Tags: elapsedSec: {}", elapsedSec);
                            logger.info("injectScte35Tags: scte35Muxer.getSCTE35TagType(): {}", scte35Muxer.getSCTE35TagType());
                            switch (scte35Muxer.getSCTE35TagType()) {
                                case EXT_X_CUE_OUT_IN:
                                    // Emit opening CUE-OUT only once at the beginning of the ad break
                                    if (!cueOutInjected) {
                                        if (cue.duration > 0) {
                                            newContent.append(String.format("#EXT-X-CUE-OUT:%.3f\n", cue.duration / 90000.0));
                                        } else {
                                            newContent.append("#EXT-X-CUE-OUT\n");
                                        }
                                        cueOutInjected = true;
                                    }
                                    // CUE-OUT-CONT should be updated for every segment while the cue is active
                                    newContent.append(String.format("#EXT-X-CUE-OUT-CONT:Elapsed=%.3f\n", elapsedSec));
                                    break;
                                case EXT_X_SCTE35:
                                    newContent.append(String.format("#EXT-X-SCTE35:CUE=\"%s\",CUE-OUT-CONT=YES,ELAPSED=%.3f\n",
                                            cue.base64Data, elapsedSec));
                                    break;
                                case EXT_X_SPLICEPOINT_SCTE35:
                                    newContent.append(String.format("#EXT-X-SPLICEPOINT-SCTE35:%s\n", cue.base64Data));
                                    break;
                                case EXT_X_DATERANGE:
                                    String startDate = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();
                                    newContent.append(String.format("#EXT-X-DATERANGE:ID=\"%d\",START-DATE=\"%s\",ELAPSED=%.3f,SCTE35-OUT-CONT=%s\n",
                                            cue.eventId, startDate, elapsedSec,
                                            "0x" + bytesToHex(java.util.Base64.getDecoder().decode(cue.base64Data))));
                                    break;
                            }
                        }
                    }
                }
                newContent.append(line).append("\n");
            }
            logger.info("injectScte35Tags: newContent: {}", newContent.toString());
            return newContent.toString();

        } catch (Exception e) {
            logger.error("Error injecting SCTE-35 tags on the fly for stream {}: {}", streamId, e.getMessage());
            return playlist;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
} 