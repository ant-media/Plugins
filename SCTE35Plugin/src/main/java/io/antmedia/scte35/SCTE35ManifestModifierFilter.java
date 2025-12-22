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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingResponseWrapper;

import io.antmedia.filter.AbstractFilter;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.scte35.SCTE35ManifestModifier.ModificationResult;

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

        // Only intercept GET requests for single‐bitrate playlists (skip master/adaptive)
        if (HttpMethod.GET.equals(httpRequest.getMethod()) && httpRequest.getRequestURI().endsWith(".m3u8") &&
                !httpRequest.getRequestURI().contains(MuxAdaptor.ADAPTIVE_SUFFIX)) {

            ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(httpResponse);
            
            try {
                chain.doFilter(request, wrapper);

                int status = wrapper.getStatus();
                if (status >= HttpServletResponse.SC_OK && status <= HttpServletResponse.SC_BAD_REQUEST) {
                    String original = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

                    String streamId = extractStreamId(httpRequest.getRequestURI());
                    String modified = injectScte35Tags(original, streamId);

                    // Write modified content to wrapper's output stream
                    wrapper.resetBuffer();
                    wrapper.getOutputStream().write(modified.getBytes(StandardCharsets.UTF_8));
                }
            }
            finally {
                // Always copy wrapper's buffer to actual response
                wrapper.copyBodyToResponse();
            }
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
//            logger.info("injectScte35Tags: playlist: {}, streamId: {}", playlist, streamId);
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

            // Use the SCTE35ManifestModifier to inject tags
            ModificationResult result = SCTE35ManifestModifier.injectSCTE35Tags(
                playlist,
                scte35Muxer.getActiveCues(),
                scte35Muxer.getSCTE35TagType()
            );

            // Return modified playlist if modifications were made, otherwise return original
            if (result.hasModifications()) {
                logger.debug("SCTE-35 tags injected for stream: {}", streamId);
                return result.modifiedPlaylist;
            } else {
                logger.debug("No active cues in window for stream: {}", streamId);
                return playlist;
            }

        } catch (Exception e) {
            logger.error("Error injecting SCTE-35 tags on the fly for stream {}: {}", streamId, e.getMessage());
            return playlist;
        }
    }
}
