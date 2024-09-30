package io.antmedia.test.clip_creator;

import io.antmedia.plugin.ClipCreatorConverter;
import io.antmedia.plugin.ClipCreatorPlugin;
import org.junit.Test;
import org.mockito.Mockito;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class ClipCreatorConverterTest {

    public static String VALID_MP4_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4";
    public static String INVALID_MP4_URL = "invalid_link";

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
        boolean result = ClipCreatorConverter.createMp4(txtFile, "src/test/resources/testStream.mp4");

        assertTrue(result);
        assertTrue(new File("src/test/resources/testStream.mp4").delete());
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
}
