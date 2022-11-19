package io.antmedia.plugin;

import java.util.List;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.zixi.client.ZIXI_BITRATE_CHANGED_FUNC;
import org.bytedeco.zixi.client.ZIXI_CALLBACKS;
import org.bytedeco.zixi.client.ZIXI_LOG_FUNC;
import org.bytedeco.zixi.client.ZIXI_NEW_STREAM_FUNC;
import org.bytedeco.zixi.client.ZIXI_STATUS_FUNC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.app.SampleFrameListener;
import io.antmedia.app.SamplePacketListener;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;
import static org.bytedeco.zixi.global.client.*;
import org.bytedeco.zixi.global.client;

@Component(value="io.antmedia.zixi.client")
public class ZixiClient implements ApplicationContextAware, IStreamListener{

	protected static Logger logger = LoggerFactory.getLogger(ZixiClient.class);
	
	private Vertx vertx;
	private SampleFrameListener frameListener = new SampleFrameListener();
	private SamplePacketListener packetListener = new SamplePacketListener();
	private ApplicationContext applicationContext;
	
	private int logLevel = 0;
	

	/* 
	private static ZIXI_LOG_FUNC loggerFunction = new ZIXI_LOG_FUNC() 
	{
		@Override
		public void call(org.bytedeco.javacpp.Pointer userData, int level, org.bytedeco.javacpp.BytePointer msg) {
			logger.info("zixi log: {}", msg.getString());
			
		}
	};
	*/

	public ZIXI_NEW_STREAM_FUNC newStreamFunction = new ZIXI_NEW_STREAM_FUNC() {

		public void call(org.bytedeco.javacpp.Pointer zixi_handler, org.bytedeco.zixi.client.ZIXI_STREAM_INFO info, org.bytedeco.javacpp.Pointer user_data) 
		{};
	};

	public ZIXI_BITRATE_CHANGED_FUNC bitrateChangedFunction = new ZIXI_BITRATE_CHANGED_FUNC() {

		public void call(org.bytedeco.javacpp.Pointer zixi_handler, int stream_index, int bitrate, BytePointer stream_name, org.bytedeco.javacpp.Pointer user_data) {};
	};

	

	public ZIXI_STATUS_FUNC statusChangedFunction = new ZIXI_STATUS_FUNC() {

		public void call(org.bytedeco.javacpp.Pointer zixi_handler, int status, org.bytedeco.javacpp.Pointer user_data) {};
	};

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
		
		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
		
		
		
	}
	
	public boolean init() {
		int result = zixi_init();
		if (result == ZIXI_ERROR_OK) {
			logger.info("Zixi is initialized successfully");
		}
		else {
			logger.warn("Zixi initialization has failed");
		}

		return result == ZIXI_ERROR_OK;
	}

	public boolean start() {
		PointerPointer<LongPointer> zixiHandlePointer = new PointerPointer<LongPointer>(1);
		zixiHandlePointer.position(0);

		int ret = zixi_init_connection_handle(zixiHandlePointer);
		Pointer zixiHandle = zixiHandlePointer.get(0);

		if (ret == 0) {
			logger.info("zixi_init_connection_handle is successfull - {}", ret);
		}
		else 
		{
			logger.warn("zixi_init_connection_handle ERROR - {}", ret);
			zixi_delete_connection_handle(zixiHandle);
			zixi_destroy();
			return false;
		}

		zixi_configure_id(zixiHandle, "AntMediaServer-ZixiPlugin", "");

		if (ret == 0) {
			logger.info("zixi_configure_id is successfull -> {}", ret);
		}
		else 
		{
			logger.warn("zixi_configure_id ERROR -> {}", ret);
			zixi_delete_connection_handle(zixiHandle);
			zixi_destroy();
			return false;
		}
		int latency = 1000;
		int fec_overhead = 30;
		int fec_block_ms  = 50;
		boolean fec_content_aware = false;
		boolean fec = false;

		//zixiHandle.position(0);
		 
		ret = zixi_configure_error_correction(zixiHandle, latency, ZIXI_LATENCY_STATIC, fec?ZIXI_FEC_ON:ZIXI_FEC_OFF, fec_overhead, fec_block_ms, fec_content_aware, false, 0, false);
		if (ret == 0) {
			logger.info("zixi_configure_error_correction is successfull -> {}", ret);
		}
		else 
		{
			logger.warn("zixi_configure_error_correction ERROR -> {}", ret);
			//TODO: it crashes in delete connection handle
			zixi_delete_connection_handle(zixiHandle);
			zixi_destroy();
			return false;
		}
		

		ZIXI_CALLBACKS cbs = new ZIXI_CALLBACKS();
		cbs.zixi_new_stream(newStreamFunction);
		cbs.zixi_status_changed(statusChangedFunction);
		cbs.zixi_bitrate_changed(bitrateChangedFunction);

		String url = "zixi://127.0.0.1:2077/stream1";
		ret = zixi_connect_url(zixiHandle, url, true, cbs, true, false, true);
		if (ret == ZIXI_ERROR_OK)
		{
			logger.info("Zixi connect url is successful to url:{}", url);
		}
		else
		{
			int ex_ret = zixi_get_last_error(zixiHandle);
			logger.warn("zixi_connect_url ERROR - {}, last error - {}", ret, ex_ret);
			zixi_delete_connection_handle(zixiHandle);
			zixi_destroy();
			return false;
		}

		byte[] data = new byte[1316*8];
		int[] writtenSize = new int[1];
		boolean[] isEOF = new boolean[1];
		boolean[] discontinuity = new boolean[1];
		int[] bitrate = new int[1];
		do {
			ret = zixi_read(zixiHandle, data, data.length, writtenSize, isEOF, discontinuity, false, bitrate);
			if (ret == ZIXI_ERROR_NOT_READY) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else {
				logger.info("zixi read ret:{} read size:{}", ret, writtenSize[0]);
			}
		} while (ret == ZIXI_ERROR_OK || ret == ZIXI_ERROR_NOT_READY);


		return true;
	}
	
	public void trycode() {
		
	//	zixi_client_configure_logging(logLevel, loggerFunction, null);
		
		
		
	//	zixi_init_connection_handle(null);
		
	//	zixi_configure_id(null, (String)null, (String)null);
		/* 
		zixi_configure_error_correction(null, ZIXI_LATENCY_INCREASING, ZIXI_LATENCY_DYNAMIC, ZIXI_FAILOVER_MODE_CONTENT, AF_CHAOS, AF_CCITT, false, false, AF_APPLETALK, false);
	
		zixi_connect_url(null, (String)null, false, null, false, false, false);
		
	//	zixi_accept(null, 0, null, false, ZIXI_PROTOCOL_HTTP)
		
		zixi_read(null, (byte[])null, _SS_ALIGNSIZE, null, (boolean[])null, null, false, (int[])null);
		
		zixi_disconnect(null);
		
		zixi_delete_connection_handle(null);
		*/
	}
	
	
		
	public MuxAdaptor getMuxAdaptor(String streamId) 
	{
		AntMediaApplicationAdapter application = getApplication();
		MuxAdaptor selectedMuxAdaptor = null;

		if(application != null)
		{
			List<MuxAdaptor> muxAdaptors = application.getMuxAdaptors();
			for (MuxAdaptor muxAdaptor : muxAdaptors) 
			{
				if (streamId.equals(muxAdaptor.getStreamId())) 
				{
					selectedMuxAdaptor = muxAdaptor;
					break;
				}
			}
		}

		return selectedMuxAdaptor;
	}
	
	public void register(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		app.addFrameListener(streamId, frameListener);		
		app.addPacketListener(streamId, packetListener);
	}
	
	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
	
	public IFrameListener createCustomBroadcast(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		return app.createCustomBroadcast(streamId);
	}

	public String getStats() {
		return frameListener.getStats() + "\t" + packetListener.getStats();
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
		
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		
	}

	

}
