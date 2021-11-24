package io.antmedia.plugin;

import java.awt.image.BufferedImage;
import java.io.IOException;

public interface IDeepLearningProcessor {
	
	/**
	 * Tries to recognize the object in the image
	 * @param data, full data of jpg or png image
	 * @throws IOException 
	 */
	public BufferedImage process(int width, int height, byte[] data, boolean immediately) throws IOException;
}