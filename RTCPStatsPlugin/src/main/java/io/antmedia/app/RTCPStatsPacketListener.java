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

/*
 * Algorithm:
 *
 * RTCP Sender Reports (SR) are sent on interval ~1-5 sec and contain synchronized NTP and RTP timestamps.
 * This data is embeded in 'AVProducerReferenceTime' packet sidedata by custom FFMPEG build.
 * To get NTP timestamp for every RTP frame, we interpolate on timing relationship established by data from TWO SRs
 *
 * How it works:
 * 1. - Clock Rate Detection (requires TWO SRs):
 *  - Wait for first SR: store its NTP and RTP timestamps
 *  - Wait for second SR: calculate time differences between the two SRs
 *  - clock rate = RTP_time_diff / NTP_time_diff (Usually 90kHz - video, 48kHz - audio)
 * 
 * 2. - Interpolation (uses most recent SR as reference):
 *  - For every RTP packet with timestamp Z, use most recent SR as reference point
 *  - Calculate: Z - SR_RTP_time = RTP time elapsed since last SR
 *  - Convert to seconds: elapsed_seconds = RTP_time_elapsed / detected_clock_rate
 *  - Interpolated NTP time = SR_NTP_time + elapsed_seconds
 * 
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!
 * REMAINDER: Time between two SRs must be at least 1 sec apart for this to work
 * Improvement 1: Re-Detect clock-rate periodically to account for possible clock drift 
 * Improvement 2: To have clock immidiately after first SR, we could do an less precise deteciton algorithm, and later switch to precise when SRs are received
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
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

		int streamIndex = packet.stream_index(); // Stream index (0 = video, 1+ = audio typically)
		long rtpDetectedClockRate = detectedClockRate.getOrDefault(streamIndex, -1L);
		if (rtpDetectedClockRate < 0) {
			// Clock rate not detected yet
			return;
		}

		try {
			AVProducerReferenceTime prft = new AVProducerReferenceTime(sideData);

			long rtcpNtpTime = prft.last_rtcp_ntp_time(); // NTP timestamp from RTCP Sender Report (64-bit: upper 32 bits = seconds since 1900, lower 32 bits = fraction)
			long rtcpRtpTime = prft.last_rtcp_timestamp(); // RTP timestamp from RTCP Sender Report (32-bit RTP clock units)
			long packetRtpTime = prft.last_rtp_timestamp(); // Current packet's RTP timestamp (32-bit RTP clock units)
			if (rtcpNtpTime == 0 || rtcpRtpTime == 0) {
				// No data...
				return;
			}
			
			// Calculate RTP timestamp difference
			// Note: RTP timestamps are 32-bit unsigned values, but Java long is signed.
			// We mask with 0xFFFFFFFFL to treat them as unsigned for proper arithmetic,
			// then handle potential wraparound (RTP timestamps wrap from 0xFFFFFFFF back to 0x00000000)
			long rtpDiff = (packetRtpTime & 0xFFFFFFFFL) - (rtcpRtpTime & 0xFFFFFFFFL);
			rtpDiff = handleRtpTimestampWraparound(rtpDiff);

			// RTP ticks to seconds
			double timeDiffSeconds = (double) rtpDiff / rtpDetectedClockRate;

			// Convert time difference to NTP fraction units (2^32 fractions per second)
			// and and add to the RTCP NTP timestamp
			long ntpFractionDiff = (long) (timeDiffSeconds * 4294967296.0);
			long interpolatedNtpTime = rtcpNtpTime + ntpFractionDiff;


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

			// Calculate RTP time difference
			long rtpDiff = handleRtpTimestampWraparound(currentRtpTime - lastRtp);

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
			logger.info("Detected RTP clock rate: {} Hz", detectedRate);
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
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
	}

	@Override
	public void writeTrailer(String streamId) {
	}
}