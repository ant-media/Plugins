package io.antmedia.app;

import java.util.ArrayList;
import java.util.List;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.NaluIndex;

import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;

public class SEItoID3Converter implements IPacketListener{

	private int packetCount = 0;
	private String streamId;
	protected static Logger logger = LoggerFactory.getLogger(SEItoID3Converter.class);

	private IAntMediaStreamHandler appAdaptor;

	public SEItoID3Converter(String streamId, IAntMediaStreamHandler appAdaptor) {
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

		List<byte[]> list = getSEIUnregisteredUnits(data);

		if (!list.isEmpty()) 
		{

			for (byte[] seiData: list) {
				
				//first 128(16 byte) bit is UUID and last 8 bit(1 byte) is the null terminated
				byte[] seiMessage = new byte[seiData.length - 16];

				System.arraycopy(seiData, 16, seiMessage, 0, seiMessage.length);
				String seiMessageString = new String(seiMessage);
				logger.info("Adding ID3 Message: {}  to streamId: {}" , seiMessageString, streamId);
				appAdaptor.getMuxAdaptor(streamId).addID3Data(seiMessageString);
			}

		}

		return pkt;
	}

	@Override
	public AVPacket onAudioPacket(String streamId, AVPacket packet) {
		packetCount++;
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

	public List<byte[]> getSEIUnregisteredUnits(byte[] buffer) 
	{
		List<NaluIndex> naluUnits = findNaluIndices(buffer);
		List<byte[]> list = new ArrayList<>();

		for (NaluIndex index : naluUnits) {

			
			//check for h264 sei message - nal unit header is 1 byte - first check is nal header, second check is payload type(5) unregistered user data
			if ((buffer[index.payloadStartOffset] & 0x1F) == 6 && buffer[index.payloadStartOffset+1] == 5) {
				//this is sei message unregistered user data for h264

				//this is unregistered user data
				
				list.add(getSEIContent(buffer, index, 2));
			}
			else 
			{
				/*
				 * NAL Header for HEVC
				 * 
				 * forbidden_zero_bit: 1 bit (must be 0)
				 * nal_unit_type: 6 bits (defines the type of NAL unit)
				 * nuh_layer_id: 6 bits
				 * nuh_temporal_id_plus1: 3 bits
				 */
				
				byte header = (byte)(buffer[index.payloadStartOffset] >> 1);
			
				//chekc for hevc sei message - nal unit header is 2 bytes - first check if it's 39 or 40, skip one byte, and check sei payload type
				
				if (((header & 0x3F) == 39 || (header & 0x3F) == 40)
							&& buffer[index.payloadStartOffset+2] == 5) 
				{
						
					list.add(getSEIContent(buffer, index, 3));
	
				}
			}
			 

		}
		return list;
	}

	private byte[] getSEIContent(byte[] buffer, NaluIndex index, int initialOffset) {
		int i = 0;
		int totalLength = 0;
		while ((buffer[index.payloadStartOffset+ initialOffset + i] & 0xFF) == 0xFF) {
			totalLength += 0xFF;
			i++;
		}
		
		totalLength += buffer[index.payloadStartOffset+ initialOffset + i] & 0xFF; //convert to unsigned
		

		//first 128(8 byte) bit is UUID and last 8 bit(1 byte) aligning
		byte[] data = new byte[totalLength]; 


		System.arraycopy(buffer, index.payloadStartOffset + initialOffset + 1 + i, data, 0, data.length);
		return data;
	}


	public List<NaluIndex> findNaluIndices(byte[] buffer) {

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

}
