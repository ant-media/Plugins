package io.antmedia.plugin.tensorflow.detection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.IDeepLearningProcessor;
import io.antmedia.plugin.tensorflow.detection.Classifier.Recognition;
import io.vertx.core.Vertx;

public class TensorFlowDetector implements IDeepLearningProcessor {

	private Classifier classifier;
	private String streamId;
	private long captureCount = 0;
	private List<Recognition> recognitionList = new ArrayList<Classifier.Recognition>();
	private Vertx vertx;
	private long lastUpdate;
	private boolean tensorflowRunning;

	private static Logger logger = LoggerFactory.getLogger(TensorFlowDetector.class);

	public TensorFlowDetector(String modelDir, Vertx vertx) throws IOException {
		this.classifier = TFObjectDetector.create(modelDir);
		this.vertx = vertx;
	}


	@Override
	public BufferedImage process(int width, int height, byte[] data, boolean immediately) throws IOException {
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
		
		if(immediately) {
			recognitionList = classifier.recognizeImage(image);
		}
		else {
			if(!tensorflowRunning) {
				tensorflowRunning = true;
				vertx.executeBlocking(a->{
					long t0 = System.currentTimeMillis();
					recognitionList = classifier.recognizeImage(image);
					logger.info("Processing time: {} ms. Number of found objects {} @{}", (System.currentTimeMillis() - t0), recognitionList.size(), System.currentTimeMillis());
					tensorflowRunning = false;
					a.complete();
				}, null);
				lastUpdate = startTime;
			}
		}

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
				
				String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/YY HH:mm:ss"));
				String text = recognition.getTitle().replaceAll("\"", "")+"("+String.format("%.02f", recognition.getConfidence())+")";
				text += " "+date;
				
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
