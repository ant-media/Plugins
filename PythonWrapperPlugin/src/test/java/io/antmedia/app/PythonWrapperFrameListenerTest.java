package io.antmedia.app;

import junit.framework.TestCase;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.mockito.Mockito.*;

public class PythonWrapperFrameListenerTest extends TestCase {

    @Test
    public void testProcessVideoFrame() throws IOException, InterruptedException {
        final int frameWidth = 640;
        final int frameHeight = 480;
        final String streamId = "streamId";
        final int videoFrameCount = 1;
        final String frameOutputName = streamId + "_" + videoFrameCount;

        AVFrame avFrame = new AVFrame();
        avFrame.width(frameWidth);
        avFrame.height(frameHeight);

        PythonWrapperFrameListener pythonWrapperFrameListener = Mockito.spy(new PythonWrapperFrameListener());
        ProcessBuilder processBuilderMock = mock(ProcessBuilder.class);
        Process processMock = mock(Process.class);
        OutputStream outputStreamMock = mock(OutputStream.class);

        when(pythonWrapperFrameListener.createPythonProcessBuilder(frameWidth, frameHeight, frameOutputName)).thenReturn(processBuilderMock);
        when(processBuilderMock.start()).thenReturn(processMock);
        when(processMock.getOutputStream()).thenReturn(outputStreamMock);

        PrintStream out = mock(PrintStream.class);
        System.setOut(out);

        when(processMock.waitFor()).thenReturn(0);
        pythonWrapperFrameListener.processVideoFrame(streamId, avFrame, videoFrameCount);

        verify(out).println(startsWith("Python script executed successfully."));

        when(processMock.waitFor()).thenReturn(-1);
        pythonWrapperFrameListener.processVideoFrame(streamId, avFrame, videoFrameCount);

        verify(out).println(startsWith("Python script execution failed."));
    }
}