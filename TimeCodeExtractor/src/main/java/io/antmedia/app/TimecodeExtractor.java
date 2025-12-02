package io.antmedia.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.NaluIndex;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;

public class TimecodeExtractor implements IPacketListener {

	private int packetCount = 0;
	private String streamId;
	protected static Logger logger = LoggerFactory.getLogger(TimecodeExtractor.class);

	Queue<String> timeCodeQueue = new ConcurrentLinkedQueue<>();
	private AntMediaApplicationAdapter appAdaptor;
	
	public TimecodeExtractor(String streamId, AntMediaApplicationAdapter appAdaptor) {
		this.streamId = streamId;
		this.appAdaptor = appAdaptor;
	}
	
	@Override
	public void writeTrailer(String streamId) {
		logger.info("SamplePacketListener.writeTrailer() for stream:{}", streamId);
	}
	

	@Override
	public AVPacket onVideoPacket(String streamId, AVPacket pkt) {
		
		packetCount++;
		byte[] data = new byte[pkt.size()];
		pkt.data().position(0).get(data, 0, data.length);
		
		List<String> list = getSEIUnregisteredUnits(data);
		
		if (!list.isEmpty()) 
		{
			timeCodeQueue.clear();
			timeCodeQueue.addAll(list);
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("timecodes", list);
			appAdaptor.sendDataChannelMessage(streamId, jsonObject.toJSONString());
		}
			
		return pkt;
	}
	
	@Override
	public AVPacket onAudioPacket(String streamId, AVPacket packet) {
		packetCount++;
		return packet;
	}

	@Override
	public AVPacket onDataPacket(String streamId, AVPacket packet) {
		return packet;
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		logger.info("SamplePacketListener.setVideoStreamInfo()");		
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		logger.info("SamplePacketListener.setAudioStreamInfo()");		
	}

	public String getStats() {
		return "packets:"+packetCount;
	}
	
	private List<String> getSEIUnregisteredUnits(byte[] buffer) 
	{
		List<NaluIndex> naluUnits = findNaluIndices(buffer);
		List<String> list = new ArrayList<>();
		
		for (NaluIndex index : naluUnits) {
			
			if ((buffer[index.payloadStartOffset] & 0x1F) == 6) {
				//this is sei message
				
				if (buffer[index.payloadStartOffset+1] == 5) {
					//this is unregistered user data
					int length = buffer[index.payloadStartOffset+2];
					if (length > 0) {
						byte[] data = new byte[length];
						
						System.arraycopy(buffer, index.payloadStartOffset+3, data, 0, data.length);
						
						//Frames - Seconds - Minutes - Hours - Days in Ascii format
						//there are spaces(0x20) between before and after "-" (0x2D)
						//By default it's length is 22
						list.add(new String(data, 0, data.length));
					}
					else {
						logger.warn("Negative SEI nal size found, discarding for stream:{}", streamId);
					}
				}
			}
 		}
		return list;
	}
	
	
	private List<NaluIndex> findNaluIndices(byte[] buffer) {

		//it searches 00 00 01 or 00 00 00 01 byte series
		int kNaluShortStartSequenceSize = 3;
		List<NaluIndex> naluSequence = new ArrayList<>();

		int size = 0;
		if (buffer.length >= kNaluShortStartSequenceSize) {
			int end = buffer.length - kNaluShortStartSequenceSize;

			for (int i = 0; i < end;) 
			{
				if (buffer[i+2] > 1) {
					//skip to next 2 bytes.
					i += 3;
				}
				else if (buffer[i+2] == 1 && buffer[i+1] == 0 && buffer[i] == 0) {
					// We found a start sequence, now check if it was a 3 of 4 byte one.
					NaluIndex index = new NaluIndex(i,i+3, 0);
					if (index.startOffset > 0 && buffer[index.startOffset - 1] == 0) {
						index.startOffset--;
					}
					size = naluSequence.size();
					if (size >= 1) {
						naluSequence.get(size-1).payloadSize = index.startOffset - naluSequence.get(size-1).payloadStartOffset;
					}

					naluSequence.add(index);
					i += 3;
				}
				else {
					++i;
				}
			}
		}

		size = naluSequence.size();
		if (size >= 1) {
			naluSequence.get(size-1).payloadSize = buffer.length - naluSequence.get(size-1).payloadStartOffset;
		}

		naluSequence.removeIf(naluIndex -> naluIndex.payloadSize == 0);

		return naluSequence;
	}
	
	public Queue<String> getTimeCodeQueue() {
		return timeCodeQueue;
	}

}
