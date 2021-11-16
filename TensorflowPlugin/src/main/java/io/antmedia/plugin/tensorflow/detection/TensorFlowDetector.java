package io.antmedia.plugin.tensorflow.detection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.IDeepLearningProcessor;

public class TensorFlowDetector implements IDeepLearningProcessor {

	private Classifier classifier;
	private String streamId;
	private long captureCount = 0;

	private static Logger logger = LoggerFactory.getLogger(TensorFlowDetector.class);

	public TensorFlowDetector(String modelDir) throws IOException {
		this.classifier = TFObjectDetector.create(modelDir);
	}


	@Override
	public BufferedImage process(int width, int height, byte[] data) throws IOException {
		long startTime = System.currentTimeMillis();

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		int k = 0;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				int r = (int)(data[k++]& 0xFF);
				int g = (int)(data[k++]& 0xFF);
				int b = (int)(data[k++]& 0xFF);
				int a = (int)(data[k++]& 0xFF);

				Color c = new Color(r, g, b);
				image.setRGB(x, y, c.getRGB());
			}
		}

		List<Classifier.Recognition> recognitionList = classifier.recognizeImage(image);

		logger.info("Processing time: {} ms. Number of found objects {}", (System.currentTimeMillis() - startTime), recognitionList.size());
		if (recognitionList.size() > 0) {
			Graphics2D g2D = image.createGraphics();
			g2D.setStroke(new BasicStroke(3));
			for (Classifier.Recognition recognition : recognitionList) {

				g2D.setColor(Color.RED);
				Rectangle rectangle = new Rectangle((int) recognition.getLocation().getMinX(),
						(int) recognition.getLocation().getMinY(), 
						(int) (recognition.getLocation().getWidth() + 0.5),
						(int) (recognition.getLocation().getHeight() + 0.5));

				g2D.draw(rectangle);
				String text = recognition.getTitle().replaceAll("\"", "")+"("+String.format("%.02f", recognition.getConfidence())+")";
				
				FontMetrics fm = g2D.getFontMetrics();
                Rectangle2D rect = fm.getStringBounds(text, g2D);
                g2D.fillRect((int)recognition.getLocation().getMinX(), 
                		(int) recognition.getLocation().getMinY() - fm.getAscent(),
                		(int) rect.getWidth(),
                		(int) rect.getHeight());

				g2D.setColor(Color.white);
				g2D.drawString(text, (int)recognition.getLocation().getMinX(), (int) recognition.getLocation().getMinY());
			}

			captureCount++;
		}
		return image;
	}
}
