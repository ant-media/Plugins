package io.antmedia.test;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.webrtc.NaluIndex;

public class SEITimeCodeTest {


	private static final int H264_NAL_SEI = 6;


	@Test
	public void testTimeCode() {
		File f = new File("src/test/resources/video_sei_timecode.h264");

		byte[] fileContent;
		try {
			fileContent = Files.readAllBytes(f.toPath());

			ByteBuffer buffer = ByteBuffer.wrap(fileContent);

			List<NaluIndex> naluIndexes = findNaluIndices(buffer);
			
			System.out.println("Number of indexes: " + naluIndexes.size());
			
			int seiIndex = 0;
			for (NaluIndex naluIndex : naluIndexes) 
			{
				//System.out.println("Start offset: " + naluIndex.startOffset + 
				//		" payload start offset: " + naluIndex.payloadStartOffset +
				//		" payload size: " + naluIndex.payloadSize);
				
//				String hexString = Integer.toHexString();
//				System.out.println("Nal unit header: " + hexString);
				
				int nalType = fileContent[naluIndex.payloadStartOffset] & 0x1F;
				seiIndex++;
				
				if (nalType == H264_NAL_SEI) {
					int payloadType = 0;
					
					//System.out.println("SEI nal unit type found. Index: " + seiIndex + " paylooad size: " + naluIndex.payloadSize);
					
					byte[] sei = new byte[naluIndex.payloadSize];
					System.arraycopy(fileContent, naluIndex.payloadStartOffset, sei, 0, sei.length);
					Parser parser = new Parser(sei, 0);
					
					/*
					Likely, the first byte(after nal header) is payload type
					the second byte is sei payload size 
					The full format is 
					
					7.3.2.3.1 Supplemental enhancement information message syntax
					
					*/
					
					//if (sei[1] == 5) 
					{
						System.out.println("sei type=" + sei[1] + " sei payload length:" + sei[2]);
					}
					
					
					
					
				}
				
			}
			

		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}


	private List<NaluIndex> findNaluIndices(ByteBuffer buffer) {

		//it searches 00 00 01 or 00 00 00 01 byte series
		int kNaluShortStartSequenceSize = 3;
		List<NaluIndex> naluSequence = new ArrayList<>();

		int size = 0;
		if (buffer.limit() >= kNaluShortStartSequenceSize) {
			int end = buffer.limit() - kNaluShortStartSequenceSize;

			for (int i = 0; i < end;) 
			{
				if (buffer.get(i+2) > 1) {
					//skip to next 3 bytes.
					i += 3;
				}
				else if (buffer.get(i+2) == 1 && buffer.get(i+1) == 0 && buffer.get(i) == 0) {
					// We found a start sequence, now check if it was a 3 of 4 byte one.
					NaluIndex index = new NaluIndex(i,i+3, 0);
					if (index.startOffset > 0 && buffer.get(index.startOffset - 1) == 0) {
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
			naluSequence.get(size-1).payloadSize = buffer.limit() - naluSequence.get(size-1).payloadStartOffset;
		}

		naluSequence.removeIf(naluIndex -> naluIndex.payloadSize == 0);

		return naluSequence;
	}
	
	public class Parser {
		
		protected int currentBit;
		protected byte[] data;
		
		protected boolean errorOccured = false;
		
		public Parser(byte[] data, int offset) {
			this.data = data;
			currentBit = offset * 8;
		}
		
		protected int readBit()
		{
		    int nIndex = currentBit / 8;
		    int nOffset = currentBit % 8 + 1;

		    currentBit ++;
		    return (data[nIndex] >> (8-nOffset)) & 0x01;
		}

		
		protected int readBits(int n)
		{
		    int r = 0;
		    int i;
		    for (i = 0; i < n; i++)
		    {
		        r |= ( readBit() << ( n - i - 1 ) );
		    }
		    return r;
		}
		

		protected int readExponentialGolombCode()
		{
		    int r = 0;
		    int i = 0;

		    while( (readBit() == 0) && (i < 32) )
		    {
		        i++;
		    }

		    r = readBits(i);
		    r += (1 << i) - 1;
		    return r;
		}

		
		protected int readSE() 
		{
		    int r = readExponentialGolombCode();
		    if ((r & 0x01) != 0x0)
		    {
		        r = (r+1)/2;
		    }
		    else
		    {
		        r = -(r/2);
		    }
		    return r;
		}
		
		public boolean isErrorOccured() {
			return errorOccured;
		}

	}


}
