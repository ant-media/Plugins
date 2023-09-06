package io.antmedia.app;

import junit.framework.TestCase;
import org.bson.assertions.Assertions;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.junit.Test;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;

public class UtilsTest extends TestCase {

    @Test
    public void testToRGB() {
        // Create a sample input AVFrame
        AVFrame testFrame = new AVFrame();
        testFrame.width(640);
        testFrame.height(480);
        testFrame.format(AV_PIX_FMT_YUV420P);

        // Call the toRGB method
        AVFrame outFrame = Utils.toRGB(testFrame);

        // Assert that the output frame is not null
        Assertions.assertNotNull(outFrame);

        // Free the output frame
        AVFramePool.getInstance().releaseFrames();
    }
}
