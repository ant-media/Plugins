package io.antmedia.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
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
import io.antmedia.model.Endpoint;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.model.Result;
import io.github.bonigarcia.wdm.WebDriverManager;

@Component(value="plugin.mediaPushPlugin")
public class MediaPushPlugin implements ApplicationContextAware, IStreamListener{



	protected static Logger logger = LoggerFactory.getLogger(MediaPushPlugin.class);

	private Map<String, RemoteWebDriver> drivers = new ConcurrentHashMap<>();

	private ApplicationContext applicationContext;



	public Map<String, RemoteWebDriver> getDrivers() {
		return drivers;
	}


	public static final int TIMEOUT_IN_SECONDS = 30;


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

		this.applicationContext = applicationContext;

		AntMediaApplicationAdapter app = getApplication();
		logger.info("MediaPushPlugin is setting up chrome driver for {}", app.getName());

		app.addStreamListener(this);

		//Setup driver gently 

		app.getVertx().executeBlocking(()-> {
			try {
				WebDriverManager.chromedriver().setup();
				logger.info("Chrome Driver setup is completed for {}", app.getName());
			}
			catch (Exception e) {
				logger.error(ExceptionUtils.getStackTrace(e));

			}
			return null;
		});
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
			logger.error("Command cannot be executed: {} " , e.getMessage());
			return new Result(false, "Command cannot be executed.");
		}
	}

	public static boolean isValidURL(String urlString) {
		try {
			new URL(urlString);
			return true;
		} catch (MalformedURLException e) {
			return false;
		}
	}

	public Result startMediaPush(String streamId, String websocketUrl, String publisherJSUrl, Endpoint request)  
	{	
		Result result = new Result(false);

		String url = request.getUrl();
		if (!isValidURL(url)) 
		{
			result.setMessage("Incoming url: "+ url +" is not a valid url");
			logger.info("Incoming url: {} is not a valid url", url);
			return result;
		}

		if (StringUtils.isNotBlank(streamId) && getDrivers().containsKey(streamId)) {
			logger.warn("Session with the same streamId: {} already exists. Pleaseo stop it first", streamId);
			result.setMessage("Session with the same streamId: "+ streamId +" already exists. Please stop it first");
			return result;
		}


		if (StringUtils.isBlank(streamId)) {
			//generate a stream id
			streamId = RandomStringUtils.randomAlphanumeric(12) + System.currentTimeMillis();
		}



		return startBroadcastingWithBrowser(streamId, websocketUrl, publisherJSUrl, request, url);

	}

	public Result startBroadcastingWithBrowser(String streamId, String websocketUrl, String publisherJSUrl,
			Endpoint request, String url) {
		Result result = new Result(false);
		RemoteWebDriver driver = null;
		try {
			driver = createDriver(request.getWidth(), request.getHeight(), streamId);
			driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(TIMEOUT_IN_SECONDS));
			driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(TIMEOUT_IN_SECONDS));

			drivers.put(streamId, driver);
			driver.get(url);

			driver.executeScript(
					"var s=window.document.createElement('script');"
							+ "s.src='"+publisherJSUrl+"';"

				+ "s.async=false;"

				+ "s.type='module';"

				+ "window.document.head.appendChild(s);"

					);


			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_IN_SECONDS));

			//there are three methods in javascript side
			//window.startBroadcasting = startBroadcasting -> gets message json parameter
			//window.stopBroadcasting = stopBroadcasting -> gets message json parameter
			//window.isConnected = isConnected; -> gets streamId 

			wait.until(ExpectedConditions.jsReturnsValue("return (typeof window.startBroadcasting != 'undefined')"));


			driver.executeScript("window.startBroadcasting({"
					+ "websocketURL:'"+websocketUrl +"',"
					+ "streamId:'" + streamId + "',"
					+ "width:" + request.getWidth() + ","
					+ "height:" + request.getHeight() + ","
					+ "token:'" + (StringUtils.isNotBlank(request.getToken()) ? request.getToken() : "") + "'," 
					+ "});");


			// wait until stream is started
			wait.until(ExpectedConditions.jsReturnsValue("return window.isConnected('"+streamId+"')"));


			result.setDataId(streamId);
			result.setSuccess(true);

		}
		catch (IOException e) 
		{
			//This exception is fired by createDriver
			result.setMessage("IOException occured in creating the chrome session. Error is " + e.getMessage());
			logger.error(ExceptionUtils.getStackTrace(e));
		}
		catch (TimeoutException e) {
			result.setMessage("Timeoutexception occured in creating the chrome session for url: " + request.getUrl() + " error message is " + e.getMessage());
			logger.error(ExceptionUtils.getStackTrace(e));

			if (driver != null) {
				LogEntries entry = driver.manage().logs().get(LogType.BROWSER);
				List<LogEntry> logs= entry.getAll();
				for(LogEntry log: logs)
				{
					logger.info(log.toString());
				}

				driver.quit();
			}

			drivers.remove(streamId);


		}
		catch(InvalidArgumentException e) {
			result.setMessage("Invalidargument exception. Error message is " + e.getMessage());
			logger.error(ExceptionUtils.getStackTrace(e));
			if (driver != null) {
				driver.quit();
			}
			drivers.remove(streamId);
		}
		return result;
	}

	public Result stopMediaPush(String streamId) {
		Result result = new Result(false);
		if (!drivers.containsKey(streamId)) 
		{
			logger.warn("Driver does not exist for stream id: {}", streamId);
			result.setMessage("Driver does not exist for stream id: " + streamId);
			return result;
		}

		RemoteWebDriver driver = drivers.remove(streamId);

		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_IN_SECONDS));

		try 
		{
			driver.executeScript("window.stopBroadcasting({"
					+ "streamId:'" + streamId + "',"
					+ "});");

			wait.until(ExpectedConditions.jsReturnsValue("return !window.isConnected('"+streamId+"')"));
			result.setSuccess(true);

		}
		catch(TimeoutException e) {
			logger.error(ExceptionUtils.getStackTrace(e));
			result.setMessage("Timeoutexception occured in stopping the stream. Fortunately, it'll quit the session to stop completely. Error message is " + e.getMessage());

		}
		finally {
			driver.quit();
		}
		return result;
	}

	public RemoteWebDriver createDriver(int width, int height, String streamId) throws IOException 
	{
		logger.info("Creating chrome session for streamId:{} and windows wxh:{}x{}", streamId, width, height);
		File logFolder = new File("log");

		if (logFolder.exists() || logFolder.mkdir()) 
		{
			//if this file does not exist, chrome fails to start
			System.setProperty("webdriver.chrome.logfile", "log/chromedriver.log");
		}
		System.setProperty("webdriver.chrome.verboseLogging", "true");


		ChromeOptions options = new ChromeOptions();
		List<String> args = new ArrayList<>();

		args.add("--remote-allow-origins=*");
		args.add("--enable-usermedia-screen-capturing");
		args.add("--allow-http-screen-capture");
		args.add("--disable-infobars");
		args.add("--hide-scrollbars");
		args.add("--auto-accept-this-tab-capture");
		args.add("--no-sandbox");
		args.add("--autoplay-policy=no-user-gesture-required");
		if (height > 0 && width > 0) {
			args.add(String.format("--window-size=%s,%s", width, height));
		}
		else {
			logger.info("creating the driver with default width and height for streamId:{}", streamId);
		}
		args.add("--headless=new");
		//args.add("--start-fullscreen");
		//args.add("--kiosk");
		args.add("--disable-gpu");

		options.setExperimentalOption("useAutomationExtension", false);
		options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
		options.addArguments(args);

		LoggingPreferences logPrefs = new LoggingPreferences();
		//To get console log
		logPrefs.enable(LogType.BROWSER, Level.ALL);

		options.setCapability( "goog:loggingPrefs", logPrefs);

		return new ChromeDriver(options);
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
		WebDriver driver = drivers.remove(streamId);
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
