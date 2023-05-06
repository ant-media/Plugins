package io.antmedia.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
import io.vertx.core.Vertx;

@Component(value="plugin.webpageRecordingPlugin")
public class WebpageRecordingPlugin implements ApplicationContextAware, IStreamListener{

	public static final String BEAN_NAME = "web.handler";
	protected static Logger logger = LoggerFactory.getLogger(WebpageRecordingPlugin.class);
	private final String EXTENSION_ID = "anoaibdoojapjdknicdngigmlijaanik";

	private Map<String, WebDriver> drivers = new ConcurrentHashMap<>();

	private ApplicationContext applicationContext;

	public Map<String, WebDriver> getDrivers() {
		return drivers;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;

		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
	}

	public Result startWebpageRecording(String streamId, String websocketUrl, String url) throws URISyntaxException, InterruptedException {
		if (streamId == null || streamId.isEmpty()) {
			//generate a stream id
			streamId = RandomStringUtils.randomAlphanumeric(12) + System.currentTimeMillis();
		}
		
		if (getDrivers().containsKey(streamId)) {
			logger.warn("Driver already exists for stream id: {}", streamId);
			return new Result(false, "Driver already exists for stream id: " + streamId);
		}

		WebDriver driver = createDriver();
		if (driver == null) {
			logger.error("Driver cannot created");
			return new Result(false, "Driver cannot created");
		}
		drivers.put(streamId, driver);
		driver.get(url);
		int timeout = 20;
		// wait until page is loaded
		while (driver.getTitle() == null || driver.getTitle().isEmpty()) {
			TimeUnit.SECONDS.sleep(1);

			// if page is not loaded in 20 seconds, return error to prevent infinite loop
			timeout--;
			if (timeout == 0) {
				logger.error("Timeout while loading the page");
				return new Result(false, streamId, "Timeout while loading the page");
			}
		}
		customModification(driver);
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript(String.format("window.postMessage({ command:  'WR_START_BROADCASTING', streamId: '%s', websocketURL: '%s' }, '*')", streamId, websocketUrl));
		return new Result(true, streamId, "Webpage recording started");
	}

	public void customModification(WebDriver driver) {
		// you add related selenium code here to play the video on a custom page or login to a page

		/* example code to start YouTube video
		new Actions(driver)
				.sendKeys("k")
				.perform();
		 */
	}

	public Result stopWebpageRecording(String streamId) throws InterruptedException {
		if (!drivers.containsKey(streamId)) {
			logger.warn("Driver does not exist for stream id: {}", streamId);
			return new Result(false, "Driver does not exist for stream id: " + streamId);
		}

		WebDriver driver = drivers.get(streamId);
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript(String.format("window.postMessage({ command:  'STOP', streamId: '%s' }, '*')", streamId));
		driver.quit();
		drivers.remove(streamId);
		return new Result(true, "Webpage recording stopped");
	}

	public WebDriver createDriver() throws URISyntaxException {
		WebDriverManager.chromedriver().setup();
		ChromeOptions options = new ChromeOptions();
		List<String> args = new ArrayList<>();

		args.add("--remote-allow-origins=*");
		args.add("--enable-usermedia-screen-capturing");
		args.add("--allow-http-screen-capture");
		args.add("--disable-infobars");
		args.add("--enable-tab-capture");
		args.add("--no-sandbox");
		args.add(String.format("--allowlisted-extension-id=%s", EXTENSION_ID));
		args.add("--headless=new");
		try {
			options.addExtensions(getExtensionFileFromResource());
		} catch (IOException e) {
			logger.error(e.getMessage());
			return null;
		}
		options.addArguments(args);

		return new ChromeDriver(options);
	}

	private File getExtensionFileFromResource() throws URISyntaxException, IOException {

		ClassLoader classLoader = getClass().getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream("webpage-recording-extension.crx");
		if (inputStream == null) {
			throw new IllegalArgumentException("webpage-recording-extension not found!");
		} else {
			File targetFile = new File("src/main/resources/targetFile.tmp");
			FileUtils.copyInputStreamToFile(inputStream, targetFile);
			return targetFile;
		}

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
		//No need to implement
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
