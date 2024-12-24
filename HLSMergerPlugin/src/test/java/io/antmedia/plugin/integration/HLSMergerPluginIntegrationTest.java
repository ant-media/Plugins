package io.antmedia.plugin.integration;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class HLSMergerPluginIntegrationTest {
	private static Process process;

	@Test
	public void testMultiResolutionHLS() {
		String streamId = "stream1";
//		executeProcess(ffmpegPath
//				+ " -re -i src/test/resources/test.flv -acodec copy -vcodec copy -f flv rtmp://127.0.0.1/LiveApp/"
//				+ streamId);
	}
	
	public static void executeProcess(final String command) {
		new Thread() {
			public void run() {
				try {

					process = Runtime.getRuntime().exec(command);
					InputStream errorStream = process.getErrorStream();
					byte[] data = new byte[1024];
					int length = 0;

					while ((length = errorStream.read(data, 0, data.length)) > 0) {
						System.out.println(new String(data, 0, length));
					}
				} catch (IOException e) {

					e.printStackTrace();
				}
			};
		}.start();
	}

	public static boolean isProcessAlive() {
		return process.isAlive();
	}

	public static void destroyProcess() {
		process.destroy();
	}
}
