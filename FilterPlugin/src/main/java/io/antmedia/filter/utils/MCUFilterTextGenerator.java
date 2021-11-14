package io.antmedia.filter.utils;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.MCUManager;

public class MCUFilterTextGenerator {
	
	private static Logger logger = LoggerFactory.getLogger(MCUFilterTextGenerator.class);
	
	public static String createAudioFilter(int streamCount) {
		if(streamCount == 1) {
			return "[in0]acopy[out0]";
		}
		
		String filter = "";
        for (int i = 0; i < streamCount; i++) {
            filter += "[in" + i + "]";
        }
        filter += "amix=inputs=" + streamCount + "[out0]";

        return filter;
	}

	public static String createVideoFilter(int streamCount) {
			
		int width = 360;
		int height = 240;
		String color = "black";
		int margin = 3;
		
		if(streamCount == 1) {
			return "[in0]scale="+(width)+":"+(height)+":force_original_aspect_ratio=decrease,pad=720:480:(ow-iw)/2:(oh-ih)/2:color="+color+"[out0]";
		}

		String filter = "";
		int columns = (int) Math.ceil(Math.sqrt((double)streamCount));
		int rows = (int) Math.ceil((double)streamCount/columns);
		int lastRowColumns = streamCount - (rows - 1) * columns;
		
		width = Math.min(360, 720/columns);
		height = 240*width/360;

		for (int i = 0; i < streamCount; i++) {
			filter += "[in" + i + "]scale="+(width-2*margin)+":"+(height-2*margin)+":force_original_aspect_ratio=decrease";
			filter += ",pad="+width+":"+height+":"+margin+":"+margin+":color="+color;
			filter += "[s" + i + "];";
		}
		int total = 0;
		int iterateRowsCount = (lastRowColumns == 1) ? rows-1 : rows;
		for (int i = 0; i < iterateRowsCount; i++) {
			int j = 0;
			for (j = 0; j < columns && total < streamCount; j++) {
				filter += "[s" + total + "]";
				total++;
			}
			String outLabel = rows == 1 ? ",pad=720:480:(ow-iw)/2:(oh-ih)/2[out0]" : "[l" + i + "];";
			filter += "hstack=inputs="+j+outLabel;
		}

		if (rows > 1) {
			if (lastRowColumns < columns) {
				String lastLowLabel = (lastRowColumns == 1) ? "[s" + total + "]" : "[l" + (rows - 1) + "]";
				filter += lastLowLabel + "pad=" + width * columns + ":" + height + ":" + width * (columns - lastRowColumns) / 2 + "[l" + (rows - 1) + "];";
			}
			for (int i = 0; i < rows; i++) {
				filter += "[l" + i + "]";
			}
			filter += "vstack=inputs=" + rows + ",pad=720:480:(ow-iw)/2:(oh-ih)/2[out0]";
		}
		
		logger.info("generated filter:{}", filter);

		return filter;
	}
}
