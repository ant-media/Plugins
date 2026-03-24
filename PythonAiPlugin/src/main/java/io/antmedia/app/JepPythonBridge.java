package io.antmedia.app;

import jep.MainInterpreter;
import jep.SharedInterpreter;
import jep.JepException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JepPythonBridge {

	private static final Logger logger = LoggerFactory.getLogger(JepPythonBridge.class);

	private static JepPythonBridge instance;
	private static volatile boolean jepLibPathSet = false;
	private SharedInterpreter interpreter;
	private final ExecutorService pythonExecutor;
	private volatile boolean initialized = false;

	private JepPythonBridge() {
		pythonExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "jep-python-thread");
			t.setDaemon(true);
			return t;
		});
	}

	public static synchronized JepPythonBridge getInstance() {
		if (instance == null) {
			instance = new JepPythonBridge();
		}
		return instance;
	}

	public boolean isInitialized() {
		return initialized;
	}

	private static synchronized void ensureJepLibraryPath() {
		if (jepLibPathSet) {
			return;
		}

		String pythonPath = System.getenv("PYTHONPATH");
		if (pythonPath == null || pythonPath.isBlank()) {
			logger.error("PYTHONPATH env variable is not set. "
					+ "Set it to your site-packages directory, e.g. export PYTHONPATH=/path/to/site-packages");
			throw new RuntimeException("PYTHONPATH environment variable is not set");
		}

		String jepLibPath = Paths.get(pythonPath, "jep", "libjep.so").toString();
		File jepLib = new File(jepLibPath);
		if (!jepLib.exists()) {
			logger.error("libjep.so not found at {}. Make sure JEP is installed: pip install jep", jepLibPath);
			throw new RuntimeException("libjep.so not found at " + jepLibPath);
		}

		logger.info("Setting JEP native library path from PYTHONPATH: {}", jepLibPath);
		MainInterpreter.setJepLibraryPath(jepLibPath);
		jepLibPathSet = true;
	}

	/**
	 * Initializes the JEP Python interpreter on the dedicated thread.
	 * @param pythonPluginPath filesystem path where python_plugin.py is located
	 */
	public synchronized void init(String pythonPluginPath) {
		if (initialized) {
			return;
		}
		ensureJepLibraryPath();
		try {
			pythonExecutor.submit(() -> {
				try {
					interpreter = new SharedInterpreter();
					interpreter.exec("import sys");
					interpreter.exec("sys.path.append('./plugins/')");  

					interpreter.exec("sys.path.insert(0, '" + pythonPluginPath  + "')");
					interpreter.exec("import python_plugin");

					interpreter.set("_default_cb", new ResultCallback("plugin_results"));
					interpreter.exec("python_plugin.set_java_callback('default', _default_cb)");
					interpreter.set("_face_cb", new ResultCallback("face_detections"));
					interpreter.exec("python_plugin.set_java_callback('face_detections', _face_cb)");
					interpreter.set("_yolo_general_cb", new ResultCallback("yolo_general_detections"));
					interpreter.exec("python_plugin.set_java_callback('yolo_general_detections', _yolo_general_cb)");
					interpreter.set("_pose_cb", new ResultCallback("pose_detections"));
					interpreter.exec("python_plugin.set_java_callback('pose_detections', _pose_cb)");
					interpreter.exec("python_plugin.init_python_plugin_state()");

					logger.info("JEP Python interpreter initialized with plugin path: {}", pythonPluginPath);
				} catch (JepException e) {
					logger.error("Failed to initialize JEP Python interpreter", e);
					throw new RuntimeException(e);
				}
			}).get();
			initialized = true;
		} catch (Exception e) {
			logger.error("Failed to initialize JEP", e);
			throw new RuntimeException("Failed to initialize JEP Python bridge", e);
		}
	}

	public void streamStarted(String streamId, String appName, int width, int height, String hlsUrl) {
		try {
			pythonExecutor.submit(() -> {
				try {
					interpreter.invoke("python_plugin.streamStarted", streamId, appName, width, height, hlsUrl);
				} catch (JepException e) {
					logger.error("Error calling python streamStarted for stream: {}", streamId, e);
				}
			}).get();
		} catch (Exception e) {
			logger.error("Error dispatching streamStarted for stream: {}", streamId, e);
		}
	}

	public void streamFinished(String streamId) {
		try {
			pythonExecutor.submit(() -> {
				try {
					interpreter.invoke("python_plugin.streamFinished", streamId);
				} catch (JepException e) {
					logger.error("Error calling python streamFinished for stream: {}", streamId, e);
				}
			}).get();
		} catch (Exception e) {
			logger.error("Error dispatching streamFinished for stream: {}", streamId, e);
		}
	}

	public void close() {
		try {
			pythonExecutor.submit(() -> {
				if (interpreter != null) {
					interpreter.close();
					interpreter = null;
				}
			}).get();
		} catch (Exception e) {
			logger.error("Error closing JEP interpreter", e);
		}
		pythonExecutor.shutdown();
		initialized = false;
	}
}
