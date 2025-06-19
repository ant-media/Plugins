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

public class RTCPStatsPacketListener implements IPacketListener {

	private static final Logger logger = LoggerFactory.getLogger(RTCPStatsPacketListener.class);

	private final String streamId;
	private final WebRTCApplication appAdapter;
	private final SizeTPointer sideDataPtr = new SizeTPointer(1);

	private final RTCPStatsPluginSettings settings;
	private final Map<Integer, Long> lastRtcpNptTime = new HashMap<>();

	private final JSONObject jsonResponse = new JSONObject();

	public RTCPStatsPacketListener(String streamId, AntMediaApplicationAdapter appAdapter, RTCPStatsPluginSettings settings) {
		this.streamId = streamId;
		this.appAdapter = (WebRTCApplication) appAdapter;
		this.settings = settings;
	}

	@Override
	public AVPacket onVideoPacket(String streamId, AVPacket packet) {
		processRtcpTiming(streamId, packet);
		return packet;
	}

	@Override
	public AVPacket onAudioPacket(String streamId, AVPacket packet) {
		processRtcpTiming(streamId, packet);
		return packet;
	}

	private void processRtcpTiming(String streamId, AVPacket packet) {
		if (packet == null || packet.side_data_elems() == 0 || !appAdapter.isDataChannelEnabled()) {
			return;
		}

		sideDataPtr.put(0);

		BytePointer sideData = av_packet_get_side_data(packet, AV_PKT_DATA_PRFT, sideDataPtr);
		if (sideData == null || sideDataPtr.get(0) < 1) {
			return;
		}

		try {
			AVProducerReferenceTime prft = new AVProducerReferenceTime(sideData);
			long rtcpNtpTime = prft.last_rtcp_ntp_time();
			long rtcpReceptionTime = prft.last_rtcp_reception_time();
			int packetCount = prft.last_rtcp_packet_count();
			int streamIndex = packet.stream_index();

			Long lastRtcpNptTime = this.lastRtcpNptTime.getOrDefault(streamIndex, 0L);
			if (lastRtcpNptTime.equals(0L) || !lastRtcpNptTime.equals(rtcpNtpTime)) {
				// We have a new SR update
				this.lastRtcpNptTime.put(streamIndex, rtcpNtpTime);
			} else if (settings.isUpdateOnlyOnNewSR()) {
				// No new SR update, and we only update on new SR.
				return;
			}

			jsonResponse.clear();
			jsonResponse.put(DataChannelConstants.EVENT_TYPE, RTCPStatsPlugin.RTCP_SENDER_REPORT_EVENT);
			jsonResponse.put("trackIndex", streamIndex);
			jsonResponse.put("pts", packet.pts());
			jsonResponse.put("ntpTime", rtcpNtpTime);
			jsonResponse.put("receptionTime", rtcpReceptionTime);
			jsonResponse.put("packetCount", packetCount);
			byte[] dataBytes = jsonResponse.toJSONString().getBytes(Charset.defaultCharset());

			DataChannelRouter dataChannelRouter = appAdapter.getDataChannelRouter();
			dataChannelRouter.publisherMessageReceived(streamId, dataBytes, false);

		} catch (Exception e) {
			logger.warn("Error processing RTCP timing for stream: {} - {}", streamId, e.getMessage());
		}
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