package io.antmedia.plugin;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;

import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;

public class ClipCreatorConverter {

	public static String ffmpegPath = "ffmpeg";
	static {
		String osName = System.getProperty("os.name", "").toLowerCase();
		if (osName.startsWith("mac os x") || osName.startsWith("darwin")) {
			ffmpegPath = "/usr/local/bin/ffmpeg";
		} 
	}

	protected static Logger logger = LoggerFactory.getLogger(ClipCreatorConverter.class);

	public static boolean runCommand(String command) {

		boolean result = false;

		try {
			Process processFinal = new ProcessBuilder("bash", "-c", command).start();
			new Thread(() -> {
				InputStream inputStream = processFinal.getErrorStream();
				byte[] data = new byte[1024];
				int length;
				try {
					while ((length = inputStream.read(data, 0, data.length)) > 0) {
						logger.info(new String(data, 0, length));
					}
				} catch (IOException e) {
					logger.error(ExceptionUtils.getStackTrace(e));
				}
			}).start();

			result = processFinal.waitFor() == 0;
		} catch (IOException e) {
			logger.error(ExceptionUtils.getStackTrace(e));
		} catch (InterruptedException e) {
			logger.error(ExceptionUtils.getStackTrace(e));
			Thread.currentThread().interrupt();
		} 
		
		return result;
	}

	public static void deleteFile(File file) {
		try {
			Files.delete(file.toPath());
		} catch (IOException e) {
			logger.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static boolean createMp4(File tsFileList, String outputFilePath, long exactStartTimeMs, long exactEndTimeMs) {
        
        long startTime = System.nanoTime();
        
        double exactDurationSecs = (exactEndTimeMs - exactStartTimeMs) / 1000.0;
        
        String tempMp4Path = outputFilePath + ".temp.mp4";
        String command = String.format(
			"%s -f concat -safe 0 -i %s " +
			" -r 30 -c:v libx264 -profile:v baseline -level 3.0 -pix_fmt yuv420p " +
			" -c:a aac -b:a 64k -movflags +faststart " +
			" %s",
			ffmpegPath,
			tsFileList.getAbsolutePath(),
			tempMp4Path
		);

        boolean success = runCommand(command);
        
        if (success) {
            //The 2nd command is to trim the exact duration 
            //because ts file lengths on the above command are not exact 
            //and can't be trusted to be exact
            command = String.format(
                    "%s -i %s -ss 0 -t %.3f -c copy -avoid_negative_ts 1 %s",
                    ffmpegPath,
                    tempMp4Path,
                    exactDurationSecs,
                    outputFilePath
                    );
            
            success = runCommand(command);
            
            // Clean up temp file
            deleteFile(new File(tempMp4Path));
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
        logger.info("Mp4 creation duration: {} ms for {} with exact duration: {:.3f}s", duration, outputFilePath, exactDurationSecs);
        
        if (success) {
            deleteFile(tsFileList);
            return true;
        }
        return false;
    }

	public static boolean createMp4(File tsFileList, String outputFilePath) {
		
		long startTime = System.nanoTime();
		String command = String.format(
				"%s -f concat -safe 0 -i %s -c copy -bsf:a aac_adtstoasc %s",
				ffmpegPath,
				tsFileList.getAbsolutePath(),
				outputFilePath
				);

		boolean success = runCommand(command);
		
		long endTime = System.nanoTime();
		long duration = (endTime - startTime) / 1000000;  //divide by 1000000 to get milliseconds.
		logger.info("Mp4 creation duration: {} ms for {}", duration, outputFilePath);
		if (success) {
			deleteFile(tsFileList);
			return true;
		}
		return false;
	}

	public static int getResolutionHeight(String filePath) {
		AVFormatContext inputFormatContext = avformat.avformat_alloc_context();

		if (avformat_open_input(inputFormatContext, filePath, null, null) != 0) {
			logger.error("Failed to open video file {}", filePath);
			return 0;
		}

		if (avformat_find_stream_info(inputFormatContext, (AVDictionary) null) < 0) {
			logger.error("Failed to retrieve stream info for: {}", filePath);
			return 0;
		}

		AVCodecContext codecContext = null;
		int videoStreamIndex = -1;
		for (int i = 0; i < inputFormatContext.nb_streams(); i++) {
			AVStream stream = inputFormatContext.streams(i);
			AVCodec codec = avcodec.avcodec_find_decoder(stream.codecpar().codec_id());
			if (codec != null && stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
				videoStreamIndex = i;
				codecContext = avcodec.avcodec_alloc_context3(codec);
				avcodec.avcodec_parameters_to_context(codecContext, stream.codecpar());
				break;
			}
		}

		if (videoStreamIndex == -1 || codecContext == null) {
			logger.error("No video stream found in file {}", filePath);
			return 0;
		}

		int height = codecContext.height();

		avformat.avformat_close_input(inputFormatContext);

		return height;
	}

}
