package io.antmedia.app;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.enterprise.webrtc.WebRTCApplication;
import io.antmedia.enterprise.webrtc.datachannel.DataChannelConstants;
import io.antmedia.enterprise.webrtc.datachannel.DataChannelRouter;
import io.antmedia.plugin.RTCPStatsPlugin;
import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avcodec.AVProducerReferenceTime;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_DATA_PRFT;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_get_side_data;

// REMAINDER: Time between two SRs must be at least 1 sec apart for this to work
public class RTCPStatsPacketListener implements IPacketListener {

	private static final Logger logger = LoggerFactory.getLogger(RTCPStatsPacketListener.class);

	private final String streamId;
	private final WebRTCApplication appAdapter;
	private final SizeTPointer sideDataPtr = new SizeTPointer(1);

	private final RTCPStatsPluginSettings settings;

	private final JSONObject jsonResponse = new JSONObject();


	private final Map<Integer, Long> lastSrNtpTime = new HashMap<>();
	private final Map<Integer, Long> lastSrRtpTime = new HashMap<>();
	private final Map<Integer, Long> detectedClockRate = new HashMap<>();


	private StreamParametersInfo videoStreamInfo;
	private StreamParametersInfo audioStreamInfo;

	public RTCPStatsPacketListener(String streamId, AntMediaApplicationAdapter appAdapter, RTCPStatsPluginSettings settings) {
		this.streamId = streamId;
		this.appAdapter = (WebRTCApplication) appAdapter;
		this.settings = settings;
	}

	@Override
	public AVPacket onVideoPacket(String streamId, AVPacket packet) {
		if (!detectedClockRate.containsKey(packet.stream_index())) {
			detectClockRateFromRtcp(packet);
			return packet;
		}

		processTimingInterpolation(streamId, packet);
		return packet;
	}

	@Override
	public AVPacket onAudioPacket(String streamId, AVPacket packet) {
		if (!detectedClockRate.containsKey(packet.stream_index())) {
			detectClockRateFromRtcp(packet);
			return packet;
		}

		processTimingInterpolation(streamId, packet);
		return packet;
	}

	private void processTimingInterpolation(String streamId, AVPacket packet) {
		sideDataPtr.put(0);
		BytePointer sideData = av_packet_get_side_data(packet, AV_PKT_DATA_PRFT, sideDataPtr);
		if (sideData == null || sideDataPtr.get(0) < 1) {
			return;
		}

		try {
			AVProducerReferenceTime prft = new AVProducerReferenceTime(sideData);

			// ===== DATA EXTRACTION =====
			long rtcpNtpTime = prft.last_rtcp_ntp_time(); // NTP timestamp from RTCP Sender Report (64-bit: upper 32 bits = seconds since 1900, lower 32 bits = fraction)
			long rtcpRtpTime = prft.last_rtcp_timestamp(); // RTP timestamp from RTCP Sender Report (32-bit RTP clock units)
			long packetRtpTime = prft.last_rtp_timestamp(); // Current packet's RTP timestamp (32-bit RTP clock units)
			int streamIndex = packet.stream_index(); // Stream index (0 = video, 1+ = audio typically)
			if (rtcpNtpTime == 0 || rtcpRtpTime == 0) {
				return; // No data...
			}

			long rtpClockRate = detectedClockRate.getOrDefault(streamIndex, -1L);
			if (rtpClockRate < 0) {
				return; // Clock rate not detected yet
			}

			// calculate RTP timestamp difference
			long rtpDiff = (packetRtpTime & 0xFFFFFFFFL) - (rtcpRtpTime & 0xFFFFFFFFL);
			rtpDiff = handleRtpTimestampWraparound(rtpDiff);

			// RTP ticks to seconds
			double timeDiffSeconds = (double) rtpDiff / rtpClockRate;

			// Convert time difference to NTP fraction units (2^32 fractions per second)
			// and and add to the RTCP NTP timestamp
			long ntpFractionDiff = (long) (timeDiffSeconds * 4294967296.0);
			long interpolatedNtpTime = rtcpNtpTime + ntpFractionDiff;

//			{
//				// DEBUG stuff
//				long interpNtpSeconds = (interpolatedNtpTime >>> 32) & 0xFFFFFFFFL;
//				long interpNtpFraction = interpolatedNtpTime & 0xFFFFFFFFL;
//
//				long rawNtpSeconds = (rtcpNtpTime >>> 32) & 0xFFFFFFFFL;
//				long rawNtpFraction = rtcpNtpTime & 0xFFFFFFFFL;
//
//				logger.info("DEBUG - Interpolated NTP Time: {} seconds, {} fraction (raw: {})",
//						interpNtpSeconds, interpNtpFraction, interpolatedNtpTime);
//
//				logger.info("DEBUG - Raw NTP Time: {} seconds, {} fraction (raw: {})",
//						rawNtpSeconds, rawNtpFraction, rtcpNtpTime);
//			}

			// Send interpolated timing data
			jsonResponse.clear();
			jsonResponse.put(DataChannelConstants.EVENT_TYPE, RTCPStatsPlugin.RTCP_SENDER_REPORT_EVENT);
			jsonResponse.put("trackIndex", streamIndex);
			jsonResponse.put("pts", packet.pts());
			jsonResponse.put("ntpTime", interpolatedNtpTime);
			jsonResponse.put("srReceptionTime", rtcpRtpTime);
			jsonResponse.put("srNtpTime", rtcpNtpTime);

			byte[] dataBytes = jsonResponse.toJSONString().getBytes(Charset.defaultCharset());
			DataChannelRouter dataChannelRouter = appAdapter.getDataChannelRouter();
			dataChannelRouter.publisherMessageReceived(streamId, dataBytes, false);

		} catch (Exception e) {
			logger.warn("Error in timing interpolation for stream: {} - {}", streamId, e.getMessage());
		}
	}

	private void detectClockRateFromRtcp(AVPacket packet) {
		sideDataPtr.put(0);
		BytePointer sideData = av_packet_get_side_data(packet, AV_PKT_DATA_PRFT, sideDataPtr);
		if (sideData == null || sideDataPtr.get(0) < 1) {
			return;
		}

		AVProducerReferenceTime prft = new AVProducerReferenceTime(sideData);

		long currentNtpTime = prft.last_rtcp_ntp_time(); // NTP timestamp from RTCP Sender Report (64-bit: upper 32 bits = seconds since 1900, lower 32 bits = fraction)
		long currentRtpTime = prft.last_rtcp_timestamp(); // RTP timestamp from RTCP Sender Report (32-bit RTP clock units)
		long packetRtpTime = prft.last_rtp_timestamp(); // Current packet's RTP timestamp (32-bit RTP clock units)
		int trackIndex = packet.stream_index();

		Long lastNtp = lastSrNtpTime.get(trackIndex);
		Long lastRtp = lastSrRtpTime.get(trackIndex);

		lastSrNtpTime.put(trackIndex, currentNtpTime);
		lastSrRtpTime.put(trackIndex, currentRtpTime);

		if (lastNtp == null || lastRtp == null) {
			return;
		}

		if (currentNtpTime == lastNtp && currentRtpTime == lastRtp) {
			return; // We have received only one SR
		}

		try {
			// Calculate NTP time difference in seconds
			double ntpDiffSeconds = convertNtpDiffToSeconds(currentNtpTime - lastNtp);

			// Calculate RTP time difference (handle wraparound)
			long rtpDiff = currentRtpTime - lastRtp;
			rtpDiff = handleRtpTimestampWraparound(rtpDiff);

			// SR time diff is too small (unreliable)
			if (ntpDiffSeconds < 1.0) {
				logger.warn("NTP time difference too small ({} s), waiting for larger interval", String.format("%.3f", ntpDiffSeconds));
				return;
			}

			double effectiveClockRate = Math.abs(rtpDiff) / ntpDiffSeconds;
			long detectedRate = Math.round(effectiveClockRate);
			if (detectedRate < 1000 || detectedRate > 1000000) {
				logger.warn("Detected unreasonable clock rate ({} Hz) for stream {}, ignoring", detectedRate, trackIndex);
				return;
			}

			detectedClockRate.put(trackIndex, detectedRate);

			logger.info("=== RTCP SR CLOCK RATE DETECTION === Stream: {} Track: {}", streamId, trackIndex);
			logger.info("Stream {}: Detected RTP clock rate: {} Hz", trackIndex, detectedRate);
			logger.info("Based on RTCP SR interval: {} s, RTP diff: {}", String.format("%.3f", ntpDiffSeconds), rtpDiff);
			logger.info("Previous SR - NTP: {}, RTP: {}", lastNtp, lastRtp);
			logger.info("Current SR  - NTP: {}, RTP: {}", currentNtpTime, currentRtpTime);
			logger.info("=== DETECTION COMPLETE ===");

		} catch (Exception e) {
			logger.warn("Error detecting clock rate from RTCP SR for stream {}: {}", trackIndex, e.getMessage());
		}
	}

	private double convertNtpDiffToSeconds(long ntpDiff) {
		// NTP timestamp: upper 32 bits = seconds, lower 32 bits = fraction
		// Convert the 64-bit NTP difference to seconds as double

		// Extract seconds and fraction parts
		long secondsPart = ntpDiff >> 32;  // Signed shift for seconds
		long fractionPart = ntpDiff & 0xFFFFFFFFL; // Unsigned lower 32 bits
		
		// Convert fraction to decimal (2^32 fractions per second)
		double fractionSeconds = fractionPart / 4294967296.0;
		return secondsPart + fractionSeconds;
	}

	/**
	 * Handles 32-bit RTP timestamp wraparound by correcting the difference.
	 * RTP timestamps are 32-bit values that can wrap around from 0xFFFFFFFF to 0x00000000.
	 */
	private long handleRtpTimestampWraparound(long rtpDiff) {
		// If difference > 2^31, we wrapped backward (subtract 2^32)
		// If difference < -2^31, we wrapped forward (add 2^32)
		if (rtpDiff > 2147483648L) {
			// backward wraparound
			rtpDiff -= 4294967296L;
		} else if (rtpDiff < -2147483648L) {
			// forward wraparound
			rtpDiff += 4294967296L;
		}
		return rtpDiff;
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
//		logger.info("Video timeBase: {}/{}", videoStreamInfo.getTimeBase().num(), videoStreamInfo.getTimeBase().den());
		this.videoStreamInfo = videoStreamInfo;
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		this.audioStreamInfo = audioStreamInfo;
	}

	@Override
	public void writeTrailer(String streamId) {
	}
}