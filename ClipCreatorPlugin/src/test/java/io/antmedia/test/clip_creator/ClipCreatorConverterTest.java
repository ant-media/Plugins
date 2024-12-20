package io.antmedia.test.clip_creator;

import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.ClipCreatorConverter;
import io.antmedia.plugin.ClipCreatorPlugin;

public class ClipCreatorConverterTest {

    private static final String TARGET_TEST_STREAM_MP4 = "target/testStream.mp4";
	public static String VALID_MP4_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4";
    public static String INVALID_MP4_URL = "invalid_link";
	private static boolean audioExists;
	private static boolean videoExists;
	private static long videoStartTimeMs;
	private static long videoDuration;
	private static long audioStartTimeMs;
	private static long audioDuration;
	
	private static Logger logger = LoggerFactory.getLogger(ClipCreatorConverter.class);

	@Before
    public void before() {
        new File(TARGET_TEST_STREAM_MP4).delete();

    }

    @Test
    public void testRunCommand_Success() {
        String command = "echo Hello World";
        boolean result = ClipCreatorConverter.runCommand(command);
        assertTrue(result);
    }

    @Test
    public void testRunCommand_Failure() {
        String command = "invalidCommand";
        boolean result = ClipCreatorConverter.runCommand(command);
        assertFalse(result);
    }

    @Test
    public void testCreateMp4_Success() throws IOException {

        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        File m3u8File = new File("src/test/resources/testStream.m3u8");
        ArrayList<File> tsFilesToMerge = new ArrayList<>();
        tsFilesToMerge.add(new File("src/test/resources/testStream000000000.ts"));
        tsFilesToMerge.add(new File("src/test/resources/testStream000000001.ts"));
        tsFilesToMerge.add(new File("src/test/resources/testStream000000002.ts"));
        tsFilesToMerge.add(new File("src/test/resources/testStream000000003.ts"));

        File txtFile = plugin.writeTsFilePathsToTxt(m3u8File,tsFilesToMerge);
        boolean result = ClipCreatorConverter.createMp4(txtFile, TARGET_TEST_STREAM_MP4);

        assertTrue(result);
        assertTrue(testFile(TARGET_TEST_STREAM_MP4, 12400, true));
        
    }

    @Test
    public void testCreateMp4_Failure() {
        boolean result = ClipCreatorConverter.createMp4(new File("nofile.txt"), "src/test/resources");
        assertFalse(result);
    }

    @Test
    public void testGetResolutionHeight() {
        int resolution = ClipCreatorConverter.getResolutionHeight(VALID_MP4_URL);
        assertEquals(720, resolution);
        resolution = ClipCreatorConverter.getResolutionHeight(INVALID_MP4_URL);
        assertEquals(0, resolution);

    }
    
    public static boolean testFile(String absolutePath, int expectedDurationInMS, boolean fullRead) {
		int ret;
		audioExists = false;
		videoExists = false;
		logger.info("Tested File: {}", absolutePath);

		//AVDictionary dic = null;

		/*
		if(absolutePath.contains("mpd")) {
			findInputFormat = avformat.av_find_input_format("dash");
			av_dict_set(dic, "protocol_whitelist","mpd,mpeg,dash,m4s", 0);
		}
		 */
		AVFormatContext inputFormatContext = avformat.avformat_alloc_context();
		if (inputFormatContext == null) {
			System.out.println("cannot allocate input context");
			return false;
		}
		
		//allowed_extensions
		
		AVDictionary optionsDictionary = new AVDictionary();
			
		av_dict_set(optionsDictionary, "allowed_extensions", "ALL", 0);

		if ((ret = avformat_open_input(inputFormatContext, absolutePath, null, (AVDictionary) optionsDictionary)) < 0) {
			System.out.println("cannot open input context: " + absolutePath);
			return false;
		}

		/*
			byte[] data = new byte[2048];
			av_strerror(ret, data, data.length);
			throw new IllegalStateException("cannot open input context. Error is " + new String(data, 0, data.length));
		 */

		//av_dump_format(inputFormatContext,0,"test",0);

		ret = avformat_find_stream_info(inputFormatContext, (AVDictionary) null);
		if (ret < 0) {
			System.out.println("Could not find stream information\n");
			return false;
		}

		int streamCount = inputFormatContext.nb_streams();
		if (streamCount == 0) {
			return false;
		}

		boolean streamExists = false;
		for (int i = 0; i < streamCount; i++) {
			AVCodecParameters codecpar = inputFormatContext.streams(i).codecpar();

			if (codecpar.codec_type() == AVMEDIA_TYPE_VIDEO) 
			{
				assertTrue(codecpar.width() != 0);
				assertTrue(codecpar.height() != 0);
				assertTrue(codecpar.format() != AV_PIX_FMT_NONE);
				videoStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);
				
				videoDuration = av_rescale_q(inputFormatContext.streams(i).duration(),  inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);


				videoExists = true;
				streamExists = true;
			} else if (codecpar.codec_type() == AVMEDIA_TYPE_AUDIO) 
			{
				assertTrue(codecpar.sample_rate() != 0);
				audioStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);
				
				audioDuration = av_rescale_q(inputFormatContext.streams(i).duration(),  inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);
				audioExists = true;
				streamExists = true;
			}
		}
		if (!streamExists) {
			return streamExists;
		}

		int i = 0;
		while (fullRead || i < 3) {
			AVPacket pkt = new AVPacket();
			ret = av_read_frame(inputFormatContext, pkt);

			if (ret < 0) {
				break;

			}
			i++;
			avcodec.av_packet_unref(pkt);
			pkt.close();
			pkt = null;
		}

		if (inputFormatContext.duration() != AV_NOPTS_VALUE) {
			long durationInMS = inputFormatContext.duration() / 1000;

			if (expectedDurationInMS != 0) {
				if ((durationInMS < (expectedDurationInMS - 2000)) || (durationInMS > (expectedDurationInMS + 2000))) {
					System.out.println("Failed: duration of the stream: " + durationInMS + " expected duration is: " + expectedDurationInMS);
					return false;
				}
			}
		}

		avformat_close_input(inputFormatContext);
		return true;

	}
    
   
}
