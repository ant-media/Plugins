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

import io.antmedia.AppSettings;
import io.antmedia.filter.AbstractFilter;
import io.antmedia.filter.TokenFilterManager;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.SCTE35Plugin;

/**
 * Servlet filter that injects SCTE‐35 HLS tags on the fly while serving M3U8 playlists.
 * <p>
 * It follows the same interception pattern as {@link io.antmedia.filter.HlsManifestModifierFilter}
 * but it augments the playlist with the ad breaks held by {@link SCTE35CueTracker}.
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

        if (HttpMethod.GET.equals(httpRequest.getMethod()) && httpRequest.getRequestURI().endsWith(".m3u8")) {

            ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(httpResponse);
            
            try {
                chain.doFilter(request, wrapper);

                int status = wrapper.getStatus();
                if (status >= HttpServletResponse.SC_OK && status <= HttpServletResponse.SC_BAD_REQUEST) {
                    String original = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

                    AppSettings appSettings = getAppSettings();
                    String suffixFormat = appSettings != null ? appSettings.getHlsSegmentFileSuffixFormat() : "";

                    // ABR variants are served as streamId_360p800kbps.m3u8, so the stream id has to be parsed out of the file name
                    String streamId = TokenFilterManager.getStreamId(httpRequest.getRequestURI(), suffixFormat);

                    String modified = original;
                    if (streamId != null) {
                        //the master carries no ad break tags, it only needs its bandwidth held still
                        modified = httpRequest.getRequestURI().contains(MuxAdaptor.ADAPTIVE_SUFFIX)
                                ? pinMasterBandwidths(original, streamId)
                                : injectScte35Tags(original, streamId);
                    }

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

    private String pinMasterBandwidths(String playlist, String streamId) {
        try {
            SCTE35Plugin plugin = getPlugin();
            if (plugin == null) {
                return playlist;
            }

            String modified = plugin.pinMasterBandwidths(streamId, playlist);
            return modified != null ? modified : playlist;

        } catch (Exception e) {
            logger.error("Error pinning master playlist bandwidth for stream {}: {}", streamId, e.getMessage());
            return playlist;
        }
    }

    private String injectScte35Tags(String playlist, String streamId) {
        try {
            SCTE35Plugin plugin = getPlugin();
            if (plugin == null) {
                return playlist;
            }

            SCTE35CueTracker cueTracker = plugin.getCueTracker(streamId);
            if (cueTracker == null) {
                return playlist;
            }

            //a break with no position yet takes the live edge of the first playlist served after it
            cueTracker.anchorPendingCues(SCTE35ManifestModifier.liveEdgeTime(playlist));

            String modified = SCTE35ManifestModifier.injectSCTE35Tags(playlist, cueTracker.getCues());
            if (modified == null) {
                logger.debug("No ad break to signal for stream: {}", streamId);
                return playlist;
            }
            return modified;

        } catch (Exception e) {
            logger.error("Error injecting SCTE-35 tags for stream {}: {}", streamId, e.getMessage());
            return playlist;
        }
    }

    private SCTE35Plugin getPlugin() {
        var appContext = getAppContext();
        if (appContext == null || !appContext.containsBean(SCTE35Plugin.BEAN_NAME)) {
            return null;
        }
        return (SCTE35Plugin) appContext.getBean(SCTE35Plugin.BEAN_NAME);
    }

}
