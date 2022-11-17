package io.antmedia.plugin;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.app.SampleFrameListener;
import io.antmedia.app.SamplePacketListener;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;
import org.apache.commons.io.FileUtils;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component(value="plugin.webpageRecordingPlugin")
public class WebpageRecordingPlugin implements ApplicationContextAware, IStreamListener{

	public static final String BEAN_NAME = "web.handler";
	protected static Logger logger = LoggerFactory.getLogger(WebpageRecordingPlugin.class);
	private final String EXTENSION_ID = "anoaibdoojapjdknicdngigmlijaanik";

	private HashMap<String, WebDriver> drivers = new HashMap<String, WebDriver>();

	private Vertx vertx;
	private SampleFrameListener frameListener = new SampleFrameListener();
	private SamplePacketListener packetListener = new SamplePacketListener();
	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
		
		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
	}

	public void startWebpageRecording(String streamId, String websocketUrl, String url) throws URISyntaxException, InterruptedException {
		if (drivers.containsKey(streamId)) {
			logger.warn("Driver already exists for stream id: {}", streamId);
			return;
		}

		WebDriver driver = createDriver();
		drivers.put(streamId, driver);
		driver.get(url);
		TimeUnit.SECONDS.sleep(5);
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript(String.format("window.postMessage({ command:  'WR_START_BROADCASTING', streamId: '%s', websocketURL: '%s' }, '*')", streamId, websocketUrl));
	}

	public void stopWebpageRecording(String streamId) throws InterruptedException {
		if (!drivers.containsKey(streamId)) {
			logger.warn("Driver does not exist for stream id: {}", streamId);
			return;
		}

		WebDriver driver = drivers.get(streamId);
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript(String.format("window.postMessage({ command:  'STOP', streamId: '%s' }, '*')", streamId));
		TimeUnit.SECONDS.sleep(5);
		driver.quit();
		drivers.remove(streamId);
	}

	public WebDriver createDriver() throws URISyntaxException {
		System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");
		ChromeOptions options = new ChromeOptions();
		List<String> args = new ArrayList<>();

		args.add("--enable-usermedia-screen-capturing");
		args.add("--allow-http-screen-capture");
		args.add("--disable-infobars");
		args.add("--enable-tab-capture");
		args.add("--no-sandbox");
		args.add(String.format("--whitelisted-extension-id=%s", EXTENSION_ID));
		args.add("--headless=chrome");
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
	
	public void register(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		app.addFrameListener(streamId, frameListener);		
		app.addPacketListener(streamId, packetListener);
	}
	
	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	@Override
	public void streamStarted(String streamId) {
		System.out.println("***************");
		System.out.println("Stream Started:"+streamId);
		System.out.println("***************");
	}

	@Override
	public void streamFinished(String streamId) {
		System.out.println("***************");
		System.out.println("Stream Finished:"+streamId);
		System.out.println("***************");
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		// TODO Auto-generated method stub
		
	}

}
