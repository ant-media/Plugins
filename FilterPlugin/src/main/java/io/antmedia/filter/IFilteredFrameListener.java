package io.antmedia.filter;

import org.bytedeco.ffmpeg.avutil.AVFrame;

public interface IFilteredFrameListener {
	public void onFilteredFrame(String streamId, AVFrame frame);
}
