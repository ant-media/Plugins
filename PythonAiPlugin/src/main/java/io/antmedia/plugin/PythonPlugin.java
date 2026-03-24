package io.antmedia.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.app.JepPythonBridge;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;

@Component(value = "pythonplugin")
public class PythonPlugin implements ApplicationContextAware, IStreamListener {

  private static final String PYTHON_PLUGIN_PATH_PROPERTY = "python.plugin.path";
  private static final String DEFAULT_PYTHON_PLUGIN_PATH = "/usr/local/antmedia";

  public static final String BEAN_NAME = "web.handler";
  protected static Logger logger = LoggerFactory.getLogger(PythonPlugin.class);

  private Vertx vertx;
  private ApplicationContext applicationContext;
  private JepPythonBridge jepBridge;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
    vertx = (Vertx) applicationContext.getBean("vertxCore");

    if (!onlyCallforThisApps())
      return;

    IAntMediaStreamHandler app = getApplication();
    app.addStreamListener(this);
  }

  public ApplicationContext getApplicationContext() {
    return this.applicationContext;
  }

  public MuxAdaptor getMuxAdaptor(String streamId) {
    IAntMediaStreamHandler application = getApplication();
    MuxAdaptor selectedMuxAdaptor = null;

    if (application != null) {
      selectedMuxAdaptor = application.getMuxAdaptor(streamId);
    }

    return selectedMuxAdaptor;
  }

  private synchronized void initJep() {
    if (jepBridge == null) {
      jepBridge = JepPythonBridge.getInstance();
    }
    if (!jepBridge.isInitialized()) {
      String pluginPath = System.getProperty(PYTHON_PLUGIN_PATH_PROPERTY, DEFAULT_PYTHON_PLUGIN_PATH);
      logger.info("Initializing JEP Python bridge with plugin path: {}", pluginPath);
      jepBridge.init(pluginPath);
    }
  }

  public void register(String streamId) {
    // app.addPacketListener(streamId, packetListener);
  }

  public IAntMediaStreamHandler getApplication() {
    return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
  }

  public boolean onlyCallforThisApps() {
    return true;
    //return this.applicationContext.getApplicationName().startsWith("/LiveApp");
  }

  @Override
  public void streamStarted(String streamId) {
    initJep();

    int width = getMuxAdaptor(streamId).getVideoCodecParameters().width();
    int height = getMuxAdaptor(streamId).getVideoCodecParameters().height();

    String appName = applicationContext.getApplicationName().replace("/", "");
    String hlsUrl = "http://localhost:5080/" + appName + "/streams/" + streamId + ".m3u8";

    jepBridge.streamStarted(streamId , appName, width, height, hlsUrl);
    register(streamId);
  }

  @Override
  public void streamFinished(String streamId) {
    initJep();
    jepBridge.streamFinished(streamId);
  }

  @Override
  public void joinedTheRoom(String roomId, String streamId) {
  }

  @Override
  public void leftTheRoom(String roomId, String streamId) {
  }

}
