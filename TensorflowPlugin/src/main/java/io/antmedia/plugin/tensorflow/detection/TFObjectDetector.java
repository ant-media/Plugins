package io.antmedia.plugin.tensorflow.detection;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.tensorflow.Graph;
import org.tensorflow.Operation;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection
 */
public class TFObjectDetector implements Classifier {

    private TensorFlowInferenceInterface inference;

    // Only return this many results.
    private static final int MAX_RESULTS = 1001;

    // Config values.
    private String inputName;

    // Pre-allocated buffers.
    private Map<Integer, String> labels = new HashMap<Integer, String>();
    private float[] outputLocations;
    private float[] outputScores;
    private float[] outputClasses;
    private float[] outputNumDetections;
    private String[] outputNames;

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param modelFilePath The filepath of the model GraphDef protocol buffer.
     * @param labelFilePath The filepath of label file for classes.
     */
    public static Classifier create(final String modelDir) throws IOException {
        final TFObjectDetector d = new TFObjectDetector();

     
        try {
			loadLabels(modelDir, d.labels);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        byte[] graphDef = Utils.readAllBytesOrExit(Paths.get(modelDir,"model.pb"));
        
        try (Graph g = new Graph()) {
            g.importGraphDef(graphDef);

            d.inputName = "image_tensor";
            // The inputName node has a shape of [N, H, W, C], where
            // N is the batch size
            // H = W are the height and width
            // C is the number of channels (3 for our purposes - RGB)
            final Operation inputOp = g.operation(d.inputName);
            if (inputOp == null) {
                throw new RuntimeException("Failed to find input Node '" + d.inputName + "'");
            }
            // The outputScoresName node has a shape of [N, NumLocations], where N
            // is the batch size.
            final Operation outputOp1 = g.operation("detection_scores");
            if (outputOp1 == null) {
                throw new RuntimeException("Failed to find output Node 'detection_scores'");
            }
            final Operation outputOp2 = g.operation("detection_boxes");
            if (outputOp2 == null) {
                throw new RuntimeException("Failed to find output Node 'detection_boxes'");
            }
            final Operation outputOp3 = g.operation("detection_classes");
            if (outputOp3 == null) {
                throw new RuntimeException("Failed to find output Node 'detection_classes'");
            }

            // Pre-allocate buffers.
            d.outputNames = new String[]{"detection_boxes", "detection_scores",
                    "detection_classes", "num_detections"};
            d.outputScores = new float[MAX_RESULTS];
            d.outputLocations = new float[MAX_RESULTS * 4];
            d.outputClasses = new float[MAX_RESULTS];
            d.outputNumDetections = new float[1];

            d.inference = new TensorFlowInferenceInterface(graphDef);

            return d;
        }
    }
    
    private static void loadLabels(String path, Map<Integer, String> labels) throws Exception {
        try {
            String text = new String(Files.readAllBytes(Paths.get(path,"label.pbtxt")), StandardCharsets.UTF_8);
			String[] items = text.replace(" ", "").split("item");
			for (String it : items) {
				String[] lines = it.split("\\r?\\n");
				int id = 0;
				String displayName = null;
				for (String line : lines) {
					if(line.startsWith("id:")) {
						id = Integer.parseInt(line.substring(line.indexOf(":")+1));
					}
					if(line.startsWith("display_name:")) {
						displayName = line.substring(line.indexOf(":")+1);
					}
				}
				labels.put(id, displayName);
			}
        
        } catch (IOException e) {
			e.printStackTrace();
		}
      }

    @Override
    public List<Recognition> recognizeImage(final BufferedImage image) {
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.

        inference.feedImage(inputName, getPixelBytes(image));

        inference.run(outputNames, false);
        // Copy the output Tensor back into the output array.
        outputLocations = new float[MAX_RESULTS * 4];
        outputScores = new float[MAX_RESULTS];
        outputClasses = new float[MAX_RESULTS];
        outputNumDetections = new float[1];
        inference.fetch(outputNames[0], outputLocations);
        inference.fetch(outputNames[1], outputScores);
        inference.fetch(outputNames[2], outputClasses);
        inference.fetch(outputNames[3], outputNumDetections);

        // Find the best detections.
        final PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(
                        1,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(final Recognition lhs, final Recognition rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });
        
        // Scale them back to the input size.
        for (int i = 0; i < outputScores.length; ++i) {
            float xmin = outputLocations[4 * i + 1] * image.getWidth();
            float ymin = outputLocations[4 * i] * image.getHeight();
            float xmax = outputLocations[4 * i + 3] * image.getWidth();
            float ymax = outputLocations[4 * i + 2] * image.getHeight();
            final Rectangle2D detection =
                    new Rectangle2D.Float(
                            xmin,
                            ymin,
                            xmax - xmin,
                            ymax - ymin) {
                    };
            if (outputScores[i] > 0.4) {
                pq.add(new Recognition("" + i, labels.get((int) outputClasses[i]), outputScores[i], detection));
            }
        }

        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        for (int i = 0; i < Math.min(pq.size(), MAX_RESULTS); ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }

    private byte[][][][] getPixelBytes(BufferedImage image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        byte[][][][] featuresTensorData = new byte[1][imageHeight][imageWidth][3];

        int[][] imageArray = new int[imageHeight][imageWidth];
        for (int row = 0; row < imageHeight; row++) {
            for (int column = 0; column < imageWidth; column++) {
                imageArray[row][column] = image.getRGB(column, row);
                int pixel = image.getRGB(column, row);

                byte red = (byte)((pixel >> 16) & 0xff);
                byte green = (byte)((pixel >> 8) & 0xff);
                byte blue = (byte)(pixel & 0xff);
                featuresTensorData[0][row][column][0] = red;
                featuresTensorData[0][row][column][1] = green;
                featuresTensorData[0][row][column][2] = blue;
            }
        }
        return featuresTensorData;
    }
}