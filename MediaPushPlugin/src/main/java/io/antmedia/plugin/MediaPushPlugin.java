package io.antmedia.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.antmedia.Model.ChromeExtensionInfo;
import io.antmedia.Model.Endpoint;
import io.antmedia.recorder.FrameRecorder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.model.Result;
import io.github.bonigarcia.wdm.WebDriverManager;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component(value="plugin.mediaPushPlugin")
public class MediaPushPlugin implements ApplicationContextAware, IStreamListener{

	public static final String BEAN_NAME = "web.handler";
	protected static Logger logger = LoggerFactory.getLogger(MediaPushPlugin.class);

	private Map<String, WebDriver> drivers = new ConcurrentHashMap<>();

	private ApplicationContext applicationContext;

	public Map<String, WebDriver> getDrivers() {
		return drivers;
	}

	private static final String ANT_MEDIA_STATE_STARTED = "started";

	private static final String ANT_MEDIA_STATE_ERROR = "error";

	private static ChromeExtensionInfo extensionInfo;

	ObjectMapper mapper = new ObjectMapper();

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;

		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
	}

	public Result sendCommand(String streamId, String command) {
		if (!getDrivers().containsKey(streamId)) {
			logger.warn("Driver is not exists for stream id: {}", streamId);
			return new Result(false, "Driver is not exists for stream id: " + streamId);
		}
		try {
			WebDriver driver = getDrivers().get(streamId);
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript(command);
			return new Result(true, streamId, "Command executed");
		} catch (Exception e) {
			logger.error("Command cannot be executed: " + e.getMessage());
			return new Result(false, "Command cannot be executed.");
		}
	}

	public Result startMediaPush(String streamId, String websocketUrl, Endpoint request) {
		String url = request.getURL();
		if (streamId == null || streamId.isEmpty()) {
			//generate a stream id
			streamId = RandomStringUtils.randomAlphanumeric(12) + System.currentTimeMillis();
		}
		
		if (getDrivers().containsKey(streamId)) {
			logger.warn("Driver already exists for stream id: {}", streamId);
			return new Result(false, "Driver already exists for stream id: " + streamId);
		}

		WebDriver driver = createDriver(request);
		if (driver == null) {
			logger.error("Driver cannot created");
			return new Result(false, "Driver cannot created");
		}
		drivers.put(streamId, driver);
		driver.get(url);
		int timeout = 20;
		// wait until page is loaded
		while (driver.getTitle() == null || driver.getTitle().isEmpty()) {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
			}

			// if page is not loaded in 20 seconds, return error to prevent infinite loop
			timeout--;
			if (timeout == 0) {
				logger.error("Timeout while loading the page");
				driver.quit();
				drivers.remove(streamId);
				return new Result(false, streamId, "Timeout while loading the page");
			}
		}
		JavascriptExecutor js = (JavascriptExecutor) driver;

    	if (request.getToken() == null || request.getToken().isEmpty()) {
			js.executeScript(String.format("window.postMessage(" +
					"{ command:  'WR_START_BROADCASTING'," +
						"streamId: '%s'," +
						"websocketURL: '%s'," +
						"width: '%s'," +
						"height: '%s'" +
					"}, '*')", streamId, websocketUrl, request.getWidth(), request.getHeight()));
		} else {
			js.executeScript(String.format("window.postMessage(" +
					"{ command:  'WR_START_BROADCASTING'," +
						"streamId: '%s'," +
						"websocketURL: '%s'," +
						"width: '%s'," +
						"height: '%s'," +
						"token: '%s'" +
					"}, '*')", streamId, websocketUrl, request.getWidth(), request.getHeight(), request.getToken()));
		}

		// wait until stream is started
		timeout = 10;
		while (true) {
			/* For more information about chrome.runtime.sendMessage => https://developer.chrome.com/docs/extensions/reference/api/runtime#method-sendMessage
			 * chrome.runtime.sendMessage(
				  extensionId?: string,
				  message: any,
				  options?: object,
				  callback?: function,
				)
			 */
			js.executeScript(String.format("chrome.runtime.sendMessage(" +
					"'%s'," +
					"{'streamId': '%s'}," +
					"null," +
					"(response)=>{" +
						"localStorage.setItem('webRTCAdaptorState', response.webRTCAdaptorState);" +
						"localStorage.setItem('webRTCAdaptorError', response.webRTCAdaptorError)" +
					"})", extensionInfo.getId(), streamId));

			String webRTCAdaptorState = (String) js.executeScript("return localStorage.getItem('webRTCAdaptorState')");

			if (webRTCAdaptorState == null || webRTCAdaptorState.isEmpty()) {
				logger.error("WebRTC Adaptor state is null or empty");
			} else if (webRTCAdaptorState.equals(ANT_MEDIA_STATE_STARTED)) {
					return new Result(true, streamId, "Media Push started");
			} else if(webRTCAdaptorState.equals(ANT_MEDIA_STATE_ERROR)) {
				String errorMessage = (String) js.executeScript("return localStorage.getItem('webRTCAdaptorError')");
				logger.error("Error while starting the stream");
				driver.quit();
				drivers.remove(streamId);
				return new Result(false, streamId, "Error while starting the stream. Error: " + errorMessage);
			}

			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
			}

			// if stream is not started in 20 seconds, return error to prevent infinite loop
			timeout--;
			if (timeout == 0) {
				logger.error("Timeout while starting the stream");
				driver.quit();
				drivers.remove(streamId);
				return new Result(false, streamId, "Timeout while starting the stream");
			}
		}
	}

	public Result stopMediaPush(String streamId) {
		if (!drivers.containsKey(streamId)) {
			logger.warn("Driver does not exist for stream id: {}", streamId);
			return new Result(false, "Driver does not exist for stream id: " + streamId, 404);
		}

		WebDriver driver = drivers.get(streamId);
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript(String.format("window.postMessage({ command:  'STOP', streamId: '%s' }, '*')", streamId));
		driver.quit();
		drivers.remove(streamId);
		return new Result(true, "Media Push stopped");
	}

	public WebDriver createDriver(Endpoint request) {
		System.setProperty("webdriver.chrome.logfile", "/usr/local/antmedia/log/chromedriver.log");
		System.setProperty("webdriver.chrome.verboseLogging", "true");

		WebDriverManager.chromedriver().setup();
		ChromeOptions options = new ChromeOptions();
		List<String> args = new ArrayList<>();

		args.add("--remote-allow-origins=*");
		args.add("--enable-usermedia-screen-capturing");
		args.add("--allow-http-screen-capture");
		args.add("--disable-infobars");
		args.add("--enable-tab-capture");
		args.add("--no-sandbox");
		args.add("--autoplay-policy=no-user-gesture-required");
		args.add(String.format("--allowlisted-extension-id=%s", extensionInfo.getId()));
		if (request.getHeight() > 0 && request.getWidth() > 0) {
			args.add(String.format("--window-size=%s,%s", request.getWidth(), request.getHeight()));
		}
		args.add("--headless=chrome");
		args.add("--start-fullscreen");
		args.add("--disable-gpu");
		try {
			options.addExtensions(getExtensionFileFromResource());
		} catch (IOException e) {
			logger.error(e.getMessage());
			return null;
		}
		try {
			extensionInfo = getChromeExtensionInfoFromResources();
			if (extensionInfo == null) {
				logger.error("Extension info is null");
				return null;
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
			return null;
		}
		options.setExperimentalOption("useAutomationExtension", false);
		options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
		options.addArguments(args);

		return new ChromeDriver(options);
	}

	File getExtensionFileFromResource() throws IOException {

		ClassLoader classLoader = getClass().getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream("media-push-extension.crx");
		if (inputStream == null) {
			throw new IllegalArgumentException("media-push-extension not found!");
		} else {
			File targetFile = new File("src/main/resources/targetFile.tmp");
			FileUtils.copyInputStreamToFile(inputStream, targetFile);
			return targetFile;
		}

	}

	ChromeExtensionInfo getChromeExtensionInfoFromResources() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream("extension-info.json");
		return mapper.readValue(inputStream, ChromeExtensionInfo.class);
	}
	
	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	@Override
	public void streamStarted(String streamId) {
		//No need to implement
	}

	@Override
	public void streamFinished(String streamId) {
		WebDriver driver = drivers.get(streamId);
		if (driver != null) {
			driver.quit();
			drivers.remove(streamId);
		}
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		//No need to implement
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		//No need to implement
	}

}
