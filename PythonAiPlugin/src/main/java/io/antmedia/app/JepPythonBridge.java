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
					interpreter.set("_ollama_vision_q_cb", new OllamaVisionQueueResultCallback());
					interpreter.exec("python_plugin.set_java_callback('ollama_vision_queue', _ollama_vision_q_cb)");
					interpreter.exec("python_plugin.init_python_plugin_state()");
					interpreter.set("_mon_cb", new ResultCallback("monitor_alerts"));
					interpreter.exec("python_plugin.set_monitor_callback(_mon_cb)");

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

	public void streamStarted(String streamId, String appName, int width, int height) {
		try {
			pythonExecutor.submit(() -> {
				try {
					interpreter.invoke("python_plugin.streamStarted", streamId, appName, width, height);
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

	/**
	 * Enqueue a vision analysis job (OllamaVisionQueuePlugin). Modes: analyze_image,
	 * describe_image, identify_objects, read_text, custom.
	 */
	public boolean setVisionMonitorConfig(String streamId, String promptsJson, double threshold, double intervalSec) {
		if (!initialized) {
			return false;
		}
		try {
			Object result = pythonExecutor.submit(() -> {
				try {
					return interpreter.invoke(
							"python_plugin.set_vision_monitor_config",
							streamId,
							promptsJson == null ? "[]" : promptsJson,
							threshold,
							intervalSec);
				} catch (JepException e) {
					logger.error("Error calling python set_vision_monitor_config for stream: {}", streamId, e);
					return Boolean.FALSE;
				}
			}).get();
			if (result instanceof Boolean) {
				return (Boolean) result;
			}
			return Boolean.TRUE.equals(result);
		} catch (Exception e) {
			logger.error("Error dispatching setVisionMonitorConfig for stream: {}", streamId, e);
			return false;
		}
	}

	public boolean clearVisionMonitor(String streamId) {
		if (!initialized) {
			return false;
		}
		try {
			Object result = pythonExecutor.submit(() -> {
				try {
					return interpreter.invoke("python_plugin.clear_vision_monitor", streamId);
				} catch (JepException e) {
					logger.error("Error calling python clear_vision_monitor for stream: {}", streamId, e);
					return Boolean.FALSE;
				}
			}).get();
			if (result instanceof Boolean) {
				return (Boolean) result;
			}
			return Boolean.TRUE.equals(result);
		} catch (Exception e) {
			logger.error("Error dispatching clearVisionMonitor for stream: {}", streamId, e);
			return false;
		}
	}

	public boolean enqueueOllamaVisionJob(String streamId, String mode, String userPrompt) {
		if (!initialized) {
			return false;
		}
		try {
			Object result = pythonExecutor.submit(() -> {
				try {
					return interpreter.invoke(
							"python_plugin.enqueue_ollama_vision_job",
							streamId,
							mode == null ? "" : mode,
							userPrompt == null ? "" : userPrompt);
				} catch (JepException e) {
					logger.error("Error calling python enqueue_ollama_vision_job for stream: {}", streamId, e);
					return Boolean.FALSE;
				}
			}).get();
			if (result instanceof Boolean) {
				return (Boolean) result;
			}
			return Boolean.TRUE.equals(result);
		} catch (Exception e) {
			logger.error("Error dispatching enqueueOllamaVisionJob for stream: {}", streamId, e);
			return false;
		}
	}

	public String getLatestFrameBase64(String streamId) {
		if (!initialized) {
			return null;
		}
		try {
			Object result = pythonExecutor.submit(() -> {
				try {
					return interpreter.invoke("python_plugin.get_latest_frame_b64", streamId);
				} catch (JepException e) {
					logger.error("Error calling python get_latest_frame_b64 for stream: {}", streamId, e);
					return null;
				}
			}).get();
			return result == null ? null : String.valueOf(result);
		} catch (Exception e) {
			logger.error("Error dispatching getLatestFrameBase64 for stream: {}", streamId, e);
			return null;
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
