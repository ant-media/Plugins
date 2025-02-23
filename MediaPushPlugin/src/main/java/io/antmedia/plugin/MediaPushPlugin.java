package io.antmedia.plugin;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.apache.http.entity.StringEntity;
import java.util.regex.Pattern;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.JWTFilter;
import io.antmedia.RecordType;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.TokenFilterManager;
import io.antmedia.model.Endpoint;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.model.Result;
import io.github.bonigarcia.wdm.WebDriverManager;


@Component(value=IMediaPushPlugin.BEAN_NAME)
public class MediaPushPlugin implements ApplicationContextAware, IStreamListener, IMediaPushPlugin{


	public static final String ORIGIN_IP_OF_DRIVER = "driverIp";
	public static final String MEDIA_PUSH_PUBLISHER_JS = "media-push-publisher.js";
	public static final String MEDIA_PUSH_PUBLISHER_HTML = "media-push-publisher.html";
	public static final String MEDIA_PUSH_FOLDER = "media-push";

	protected static Logger logger = LoggerFactory.getLogger(MediaPushPlugin.class);

	private Map<String, RemoteWebDriver> drivers = new ConcurrentHashMap<>();

	private Map<String, RecordType> recordingMap = new ConcurrentHashMap<>();

	private Gson gson = new Gson();


	private boolean initialized = false;
	public ApplicationContext applicationContext;


	public Map<String, RemoteWebDriver> getDrivers() {
		return drivers;
	}

	/**
	 * All official switches 
	 * https://source.chromium.org/chromium/chromium/src/+/main:ui/gl/gl_switches.cc
	 * 
	 * Extra
	 * https://peter.sh/experiments/chromium-command-line-switches/
	 */
	private static final List<String> CHROME_DEFAULT_SWITHES =
			Arrays.asList(
					"--remote-allow-origins=*",
					"--enable-usermedia-screen-capturing",
					"--allow-http-screen-capture",
					"--disable-infobars",
					"--hide-scrollbars",
					"--auto-accept-this-tab-capture",
					"--no-sandbox",
					"--autoplay-policy=no-user-gesture-required",
					"--disable-background-media-suspend",
					"--disable-gpu-vsync",
					"--disable-audio-output",
					"--disable-background-timer-throttling",
					"--headless=new",
					"--start-fullscreen");

	public static final int TIMEOUT_IN_SECONDS = 30;


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

		this.applicationContext = applicationContext;

		AntMediaApplicationAdapter app = getApplication();
		logger.info("MediaPushPlugin is initializing for {}", app.getName());

		app.addStreamListener(this);

		//Setup driver gently 

		app.getVertx().executeBlocking(()-> {
			try {
				WebDriverManager.chromedriver().setup();

				ClassLoader classLoader = getClass().getClassLoader();
				File parent = new File("webapps/"+app.getName()+ File.separator + MEDIA_PUSH_FOLDER);					
				parent.mkdirs();

				boolean result = extractFile(classLoader, parent, MEDIA_PUSH_PUBLISHER_JS);
				if (result) {
					result = extractFile(classLoader, parent, MEDIA_PUSH_PUBLISHER_HTML);
				}

				initialized = result;
				if (initialized) {
					logger.info("Media Push Plugin is initialized successfully for {}", app.getName());
				}
				else {
					logger.info("Media Push Plugin is faild to initialize for {}", app.getName());
				}
			}
			catch (Exception e) {
				logger.error(ExceptionUtils.getStackTrace(e));
				logger.info("Media Push Plugin is faild to initialize for {}", app.getName());

			}
			return null;
		}, false);
	}

	private boolean extractFile(ClassLoader classLoader, File parent, String fileName) {
		boolean result = false;
		try (InputStream inputStream = classLoader.getResourceAsStream(fileName)) {


			try (FileOutputStream fos = new FileOutputStream(parent.getAbsolutePath() + File.separator + fileName)) 
			{
				byte[] data = new byte[1024];
				int length=0;
				while ((length = inputStream.read(data, 0, data.length)) >= 0) {
					fos.write(data, 0, length);
				}

			}
			result = true;

		}
		catch (IOException e) {
			logger.error(ExceptionUtils.getStackTrace(e));
		}
		return result;
	}


	@SuppressWarnings("javasecurity:S5334") //because this is Javascript
	public Result sendCommand(String streamId, String command) {
		if (!getDrivers().containsKey(streamId)) {
			logger.warn("Driver does not exist for stream id: {}", streamId);
			return new Result(false, "Driver does not exist for stream id: " + streamId);
		}
		try {
			WebDriver driver = getDrivers().get(streamId);

			waitToBeFrameAvailable(driver);

			JavascriptExecutor js = (JavascriptExecutor) driver;

			Object obj = js.executeScript(command);

			// Switch back to the default content
			driver.switchTo().defaultContent();

			return new Result(true, streamId, obj != null ? obj.toString() : "");
		} catch (Exception e) {
			logger.error("Command cannot be executed: {} ", e.getMessage());
			return new Result(false, "Command cannot be executed: " + e.getMessage());
		}
	}

	public void waitToBeFrameAvailable(WebDriver driver) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_IN_SECONDS));

		// Switch to the iframe
		wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.tagName("iframe")));
	}

	public static boolean isValidURL(String urlString) {
		try {
			new URL(urlString);
			return true;
		} catch (MalformedURLException e) {
			return false;
		}
	}



	@Override
	public Result startMediaPush(String streamIdPar, String websocketUrl, int width, int height, String url, String token, String recordTypeString) 
	{
		//String URL, int width, int height, String jsCommand, String token
		Endpoint endpoint = new Endpoint(url, width, height, null, token);
		endpoint.setRecordType(recordTypeString);
		return startMediaPush(streamIdPar, websocketUrl, endpoint);
	}

	public WebDriverWait createWebDriverWait(WebDriver driver, int timeoutSeconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
	}

	@SuppressWarnings("javasecurity:S5334")
	@Override
	public Result startMediaPush(String streamIdPar, String websocketUrl, Endpoint endpoint) 
	{

		String url = endpoint.getUrl();
		int width = endpoint.getWidth();
		int height = endpoint.getHeight();
		String token = endpoint.getToken();
		String recordTypeString = endpoint.getRecordType();
		String extraChromeSwitches = endpoint.getExtraChromeSwitches();

		List<String> extraChromeSwitchList = null;
		if (StringUtils.isNotBlank(extraChromeSwitches)) {
			extraChromeSwitchList = Arrays.asList(extraChromeSwitches.split(","));
		}

		Result result = new Result(false);

		if (!isValidURL(url)) 
		{
			result.setMessage("Incoming url: "+ url +" is not a valid url");
			if (logger.isInfoEnabled()) {
				logger.info("Incoming url: {} is not a valid url", url.replaceAll("[\n\r]", "_"));
			}
			return result;
		}

		if (StringUtils.isNotBlank(streamIdPar) && getDrivers().containsKey(streamIdPar)) {
			logger.warn("Session with the same streamId: {} already exists. Pleaseo stop it first", streamIdPar);
			result.setMessage("Session with the same streamId: "+ streamIdPar +" already exists. Please stop it first");
			return result;
		}


		String streamId = checkAndGetStreamId(streamIdPar);


		RemoteWebDriver driver = null;
		try {

			String publisherUrl = getPublisherHTMLURL(websocketUrl);

			driver = openDriver(width, height, recordTypeString, extraChromeSwitchList, streamId, publisherUrl, url);

			WebDriverWait wait = createWebDriverWait(driver, TIMEOUT_IN_SECONDS);

			//there are three methods in javascript side
			//window.startBroadcasting = startBroadcasting -> gets message json parameter
			//window.stopBroadcasting = stopBroadcasting -> gets message json parameter
			//window.isConnected = isConnected; -> gets streamId 

			wait.until(ExpectedConditions.jsReturnsValue("return (typeof window.startBroadcasting != 'undefined')"));

			String driverIp = getApplication().getServerSettings().getHostAddress();
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(ORIGIN_IP_OF_DRIVER, driverIp);


			String startBroadcastingCommand = String.format("window.startBroadcasting({websocketURL:'%s',streamId:'%s',width:%d,height:%d,token:'%s',driverIp:'%s'});", 
					websocketUrl, streamId, width, height, StringUtils.isNotBlank(token) ? token : "",jsonObject.toJSONString());

			driver.executeScript(startBroadcastingCommand);


			// wait until stream is started
			wait.until(ExpectedConditions.jsReturnsValue("return window.isConnected('"+streamId+"')"));


			result.setDataId(streamId);
			result.setSuccess(true);

		}

		catch(IOException | URISyntaxException | WebDriverException e) {
			logger.error(ExceptionUtils.getStackTrace(e));
			result.setMessage(clearAndQuit(streamId, driver, e));
		}

		return result;
	}
	@SuppressWarnings("javasecurity:S5334") //because this is Javascript
	public RemoteWebDriver openDriver(int width, int height, String recordTypeString,
			List<String> extraChromeSwitchList, String streamId, String publisherUrl, String targetUrl) throws IOException {
		RemoteWebDriver driver;
		driver = createDriver(width, height, streamId, extraChromeSwitchList);
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(TIMEOUT_IN_SECONDS));
		driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(TIMEOUT_IN_SECONDS));

		drivers.put(streamId, driver);

		for (RecordType recordType : RecordType.values()) {
			if (recordType.toString().equals(recordTypeString)) {
				recordingMap.put(streamId, recordType);
				break; // Stop the loop once a match is found
			}
		}
		logger.info("publisherUrl -> {}", publisherUrl);
		driver.get(publisherUrl);

		driver.executeScript(
				String.format("document.getElementById('media-push-iframe').src='%s'", targetUrl)
				);
		return driver;
	}

	public String checkAndGetStreamId(String streamId) {
		if (StringUtils.isBlank(streamId)) {
			//generate a stream id
			return RandomStringUtils.randomAlphanumeric(12) + System.currentTimeMillis();
		}
		return streamId;
	}

	public String clearAndQuit(String streamId, RemoteWebDriver driver, Exception e) {

		logger.error(ExceptionUtils.getStackTrace(e));
		if (driver != null) 
		{
			if (logger.isInfoEnabled()) {
				LogEntries entry = driver.manage().logs().get(LogType.BROWSER);
				List<LogEntry> logs= entry.getAll();
				for(LogEntry log: logs)
				{
					logger.info(log.toString());
				}
			}
			driver.quit();
		}
		drivers.remove(streamId);
		recordingMap.remove(streamId);
		return "Error message is " + e.getMessage();
	}
	public String getPublisherHTMLURL(String websocketUrl) throws InvalidArgumentException, URISyntaxException 
	{

		URI websocketURLObject = new URI(websocketUrl);
		String scheme = websocketURLObject.getScheme();
		if (scheme == null) {
			throw new InvalidArgumentException("Websocket URL is not valid");
		}

		String publisherHtmlURL = websocketURLObject.getScheme().contains("wss") ? "https://" : "http://";
		publisherHtmlURL += websocketURLObject.getHost();


		publisherHtmlURL +=  websocketURLObject.getPort() != -1 ? (":" + websocketURLObject.getPort()) : "";

		String path = websocketURLObject.getPath();

		//http://127.0.0.1:5080/ConferenceCall/js/media-push/publisher.js
		int lastSlashIndex = path.lastIndexOf("/");
		if (lastSlashIndex != -1) {
			publisherHtmlURL += path.substring(0, lastSlashIndex);
		}
		publisherHtmlURL +=  File.separator + MEDIA_PUSH_FOLDER + File.separator + MEDIA_PUSH_PUBLISHER_HTML;

		return publisherHtmlURL;
	}

	public boolean isValidIP(String ip) {
		if (ip == null)
			return false;
		return ip.matches("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
	}

	public boolean forwardMediaPushStopRequest(String streamId, String ip) {


		boolean result = false;
		AntMediaApplicationAdapter appAdapter = getApplication();
		logger.info("forwarding media push stop request to {}", ip);
		try {
			CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();

			String url = "http://" + ip + ":" + appAdapter.getServerSettings().getDefaultHttpPort()
					+ applicationContext.getApplicationName() + "/rest/v1/media-push/stop/" + streamId;
			logger.info(url);

			String jwtToken = JWTFilter.generateJwtToken(appAdapter.getAppSettings().getClusterCommunicationKey(),
					System.currentTimeMillis() + 5000);
			HttpUriRequest post = RequestBuilder.post().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setHeader(TokenFilterManager.TOKEN_HEADER_FOR_NODE_COMMUNICATION, jwtToken)
					.build();

			CloseableHttpResponse response = client.execute(post);

			String content = EntityUtils.toString(response.getEntity());


			Result resultResponse = gson.fromJson(content, Result.class);

			result = resultResponse.isSuccess();
			if (!result) {
				logger.error("Error in forwarding media push stop request to {} with response {} for streamId:{}", ip, content, streamId);
			}

		} catch (Exception e) {
			logger.error(ExceptionUtils.getStackTrace(e));
		}
		return result;
	}

	@Override
	public Result stopMediaPush(String streamId) {
		Result result = new Result(false);
		if (!drivers.containsKey(streamId)) {
			Broadcast broadcast = getBroadcast(streamId);

			if (broadcast == null) {
				result.setMessage("Driver does not exist for stream id: " + streamId);
				return result;
			}

			String metaData = broadcast.getMetaData();
			try {
				if (metaData != null && !metaData.equals("null") && !metaData.isEmpty()) {
					JsonObject jsonObject = JsonParser.parseString(metaData).getAsJsonObject();
					String driverIp = jsonObject.get(ORIGIN_IP_OF_DRIVER).getAsString();

					if (isValidIP(driverIp) && forwardMediaPushStopRequest(streamId, driverIp)) {
						result.setSuccess(true);
						return result;
					}
				}
				result.setMessage("Driver does not exist for stream id: " + streamId);
				return result;
			} catch (Exception e) {
				logger.error("Cannot proceed for forwarding media push stop request to driver for streamId:{} because {}", streamId, ExceptionUtils.getStackTrace(e));
				result.setMessage("");
			}
			return result;
		}

		RemoteWebDriver driver = drivers.remove(streamId);
		recordingMap.remove(streamId);

		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_IN_SECONDS));

		try {
			driver.executeScript("window.stopBroadcasting({"
					+ "streamId:'" + streamId + "',"
					+ "});");

			wait.until(ExpectedConditions.jsReturnsValue("return !window.isConnected('" + streamId + "')"));
			result.setSuccess(true);

		} catch (TimeoutException e) {
			logger.error(ExceptionUtils.getStackTrace(e));
			result.setMessage(
					"Timeoutexception occured in stopping the stream. Fortunately, it'll quit the session to stop completely. Error message is "
							+ e.getMessage());

		} finally {
			driver.quit();
		}
		return result;
	}


	@SuppressWarnings("java:S1130")
	public RemoteWebDriver createDriver(int width, int height, String streamId, List<String> extraChromeSwitchList) throws IOException 
	{
		streamId = streamId.replaceAll("[\n\r]", "_");

		logger.info("Creating chrome session for streamId:{} and windows wxh:{}x{}", streamId, width, height);
		File logFolder = new File("log");

		if (logFolder.exists() || logFolder.mkdir()) 
		{
			//if this file does not exist, chrome fails to start
			System.setProperty("webdriver.chrome.logfile", "log/chromedriver.log");
		}
		System.setProperty("webdriver.chrome.verboseLogging", "true");


		ChromeOptions options = new ChromeOptions();
		List<String> args = new ArrayList<>(CHROME_DEFAULT_SWITHES);


		if (height > 0 && width > 0) {
			args.add(String.format("--window-size=%s,%s", width, height));
		}
		else {
			logger.info("creating the driver with default width and height for streamId:{}", streamId);
		}

		if (extraChromeSwitchList != null) {
			for (String extraChromeSwitch : extraChromeSwitchList) {
				if (StringUtils.isBlank(extraChromeSwitch)) {
					continue;
				}
				args.add(extraChromeSwitch);
			}
		}

		options.setExperimentalOption("useAutomationExtension", false);
		options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
		options.addArguments(args);

		LoggingPreferences logPrefs = new LoggingPreferences();
		//To get console log
		logPrefs.enable(LogType.BROWSER, Level.ALL);

		options.setCapability( "goog:loggingPrefs", logPrefs);

		return new ChromeDriver(options);
	}


	public Broadcast getBroadcast(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		DataStore dataStore = app.getDataStore();

		if(dataStore == null)
			return null; 

		return dataStore.get(streamId);
	}


	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	@Override
	@SuppressWarnings("java:S5738")
	public void streamStarted(String streamId) 
	{
		if (recordingMap.containsKey(streamId)) 
		{
			getApplication().getMuxAdaptor(streamId).startRecording(recordingMap.get(streamId), 0);
		}
	}


	@Override
	@SuppressWarnings("java:S5738")
	public void streamFinished(String streamId) {
		WebDriver driver = drivers.remove(streamId);
		recordingMap.remove(streamId);
		if (driver != null) {
			driver.quit();
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
