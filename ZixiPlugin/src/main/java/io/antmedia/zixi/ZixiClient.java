package io.antmedia.zixi;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;


import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.av_dump_format;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avio_alloc_context;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_free;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;
import static org.bytedeco.ffmpeg.global.avutil.av_free;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.zixi.client.ZIXI_BITRATE_CHANGED_FUNC;
import org.bytedeco.zixi.client.ZIXI_CALLBACKS;
import org.bytedeco.zixi.client.ZIXI_NEW_STREAM_FUNC;
import org.bytedeco.zixi.client.ZIXI_STATUS_FUNC;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.ZixiPlugin;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;
import static org.bytedeco.zixi.global.client.*;

/**
 * ZixiClient connects to a Zixi Broadcaster(ZB) and pull the stream. It's also called Zixi Receiver because 
 * it receives the data from the Zixi Broadcaster
 * 
 * Zixi Receiver has two modes. Pulling the stream from ZB and accepting/ingesting the stream pushed by Zixi Feeder or ZB
 * AMS supports only pulling the stream from ZB
 * 
 * In other words, ZixiClient is a StreamFetcher for the Ant Media Server terminology
 * 
 * @author mekya
 *
 */
public class ZixiClient {

	protected static Logger logger = LoggerFactory.getLogger(ZixiClient.class);
	
	private Queue<byte[]> queue = new ConcurrentLinkedQueue<>();
	
	private String streamId;
	
	protected static final int BUFFER_SIZE = 8192;
	
	protected static final long WAIT_TIME_MILLISECONDS = 5;
	
	public static final long INACTIVITY_TIMEOUT_MS = 5000;
		
	private boolean streamPublished = false;

	protected AtomicBoolean isPipeReaderJobRunning = new AtomicBoolean(false);
	
	private AtomicBoolean stopRequested = new AtomicBoolean(false);

	private AtomicBoolean prepared = new AtomicBoolean(false);

	private AtomicBoolean stopped = new AtomicBoolean(false);

	/* 
	private static ZIXI_LOG_FUNC loggerFunction = new ZIXI_LOG_FUNC() 
	{
		@Override
		public void call(org.bytedeco.javacpp.Pointer userData, int level, org.bytedeco.javacpp.BytePointer msg) {
			logger.info("zixi log: {}", msg.getString());
			
		}
	};
	*/

	public AtomicBoolean getStopped() {
		return stopped;
	}

	public ZIXI_NEW_STREAM_FUNC newStreamFunction = new ZIXI_NEW_STREAM_FUNC() {

		public void call(org.bytedeco.javacpp.Pointer zixi_handler, org.bytedeco.zixi.client.ZIXI_STREAM_INFO info, org.bytedeco.javacpp.Pointer user_data) 
		{
			
		}
	};

	public ZIXI_BITRATE_CHANGED_FUNC bitrateChangedFunction = new ZIXI_BITRATE_CHANGED_FUNC() {

		public void call(org.bytedeco.javacpp.Pointer zixi_handler, int stream_index, int bitrate, BytePointer stream_name, org.bytedeco.javacpp.Pointer user_data) {
			
		}
	};

	public ZIXI_STATUS_FUNC statusChangedFunction = new ZIXI_STATUS_FUNC() {

		
		public void call(org.bytedeco.javacpp.Pointer zixi_handler, int status, org.bytedeco.javacpp.Pointer user_data) {
			
		}
	};
	
	public static final Read_packet_Pointer_BytePointer_int readCallback = new Read_packet_Pointer_BytePointer_int() {

		@Override
		public int call(Pointer opaque, BytePointer buf, int bufSize) {
			int length = -1;
			try {

				byte[] packet = null;

				ZixiClient zixiClient = socketQueueMap.get(opaque);
				long waitCount = 0;
				while ((packet = zixiClient.getQueue().poll()) == null) 
				{
					if (zixiClient.stopRequested.get()) 
					{
						logger.info("Stop request for stream id: {} and scope: {}", zixiClient.streamId, zixiClient.appAdaptor.getScope().getName());
						break;
					}
					Thread.sleep(WAIT_TIME_MILLISECONDS);

					waitCount++;
					long elapsedTimeMs = waitCount * WAIT_TIME_MILLISECONDS;
					if (waitCount % 50 == 0) 
					{
						logger.warn("Stream:{} in {} does not get packet for {} ms" , zixiClient.streamId, zixiClient.appAdaptor.getScope().getName(), elapsedTimeMs);
					}

					if (elapsedTimeMs > INACTIVITY_TIMEOUT_MS) {
						logger.warn("Inactivity timeout({}ms) for srt stream:{}/{}", INACTIVITY_TIMEOUT_MS, zixiClient.appAdaptor.getScope().getName(), zixiClient.streamId);
						zixiClient.stop();
						break;
					}
				}

				if (packet != null) 
				{
					// ** this setting critical..
					length = packet.length;
					buf.put(packet, 0, length);
				}

			} 
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.error(ExceptionUtils.getStackTrace(e));
			}
			catch (Exception e) 
			{
				logger.error(ExceptionUtils.getStackTrace(e));
			}

			return length;
		}
	};


	private Vertx vertx;


	private AntMediaApplicationAdapter appAdaptor;


	private String streamUrl;


	private Pointer zixiHandle = null;

	private AVFormatContext inputFormatContext;

	private BytePointer opaque;

	public static final Map<Pointer, ZixiClient> socketQueueMap = new ConcurrentHashMap<>();


	private AVIOContext avioContext;

	private MuxAdaptor muxAdaptor;

	private AVPacket pkt;

	private long readTimer;

	private long lastReceivedPacketTimeMs = -1;

	public ZixiClient(Vertx vertx, AntMediaApplicationAdapter appAdaptor, String streamUrl, String streamId) {
		this.vertx = vertx;
		this.appAdaptor = appAdaptor;
		//"zixi://127.0.0.1:2077/stream1
		this.streamUrl = streamUrl;
		this.streamId = streamId;
	}
	
	public boolean connect() 
	{
		PointerPointer<LongPointer> zixiHandlePointer = new PointerPointer<LongPointer>(1);
		zixiHandlePointer.position(0);

		int ret = zixi_init_connection_handle(zixiHandlePointer);
		if (ret == 0) {
			logger.info("zixi_init_connection_handle is successfull - {}", ret);
		}
		else 
		{
			logger.warn("zixi_init_connection_handle ERROR - {}", ret);
			disconnect();
			return false;
		}
		
		zixiHandle = zixiHandlePointer.get(0);

		zixi_configure_id(zixiHandle, "AntMediaServer-ZixiPlugin", "");

		if (ret == 0) {
			logger.info("zixi_configure_id is successfull -> {}", ret);
		}
		else 
		{
			logger.warn("zixi_configure_id ERROR -> {}", ret);
			disconnect();
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
			disconnect();
			return false;
		}
		

		ZIXI_CALLBACKS cbs = new ZIXI_CALLBACKS();
		cbs.zixi_new_stream(newStreamFunction);
		cbs.zixi_status_changed(statusChangedFunction);
		cbs.zixi_bitrate_changed(bitrateChangedFunction);

		ret = zixi_connect_url(zixiHandle, streamUrl, true, cbs, true, false, true);
		if (ret == ZIXI_ERROR_OK)
		{
			logger.info("Zixi connect url is successful to url:{}", streamUrl);
		}
		else
		{
			int ex_ret = zixi_get_last_error(zixiHandle);
			logger.warn("zixi_connect_url ERROR - {}, last error - {}", ret, ex_ret);
			disconnect();
			return false;
		}

		return true;
		
	}
	
	public synchronized boolean disconnect() {
		
		boolean disconnected = false; 
		
		if (zixiHandle != null && zixi_disconnect(zixiHandle) == ZIXI_ERROR_OK) {
			logger.info("Zixi client disconnect is successful for url:{}", streamUrl);
			disconnected = true;
		} 
		else {
			logger.info("Zixi client disconnect is failed for url:{}", streamUrl);
		}
		
		boolean connectionDeleted = false;
		if (zixiHandle != null && zixi_delete_connection_handle(zixiHandle) == ZIXI_ERROR_OK) {
			logger.info("Zixi client delete connection is successful for url:{}", streamUrl);
			connectionDeleted = true;
			zixiHandle = null;
		} 
		else {
			logger.info("Zixi client delete connection is failed for url:{}", streamUrl);
		}
	
		return disconnected && connectionDeleted;
	}	
	
	public synchronized AVFormatContext prepareContext() 
	{
		try 
		{
			logger.info("Preparing Zixi Client stream:{} and scope:{}", streamId, appAdaptor.getScope().getName());
			inputFormatContext = avformat.avformat_alloc_context();

			opaque = new BytePointer(streamId);
			socketQueueMap.put(opaque, this);


			avioContext = avio_alloc_context(new BytePointer(avutil.av_malloc(BUFFER_SIZE)), BUFFER_SIZE, 0,
					opaque, readCallback, null, null);

			inputFormatContext.pb(avioContext);

			AVDictionary optionsDictionary = new AVDictionary();

			int analyzeDurationUs = appAdaptor.getAppSettings().getMaxAnalyzeDurationMS()  * 1000 * 10;
			String analyzeDuration = String.valueOf(analyzeDurationUs);
			av_dict_set(optionsDictionary, "analyzeduration", analyzeDuration, 0);


			logger.info("avformat_open_input");
			if (avformat.avformat_open_input(inputFormatContext, (String) null, null,
					optionsDictionary) < 0) 
			{
				logger.error("Cannot open input context for stream: {}", streamId);
				av_dict_free(optionsDictionary);
				optionsDictionary.close();
				return null;
			}

			av_dict_free(optionsDictionary);
			optionsDictionary.close();

			logger.info("Try to get stream info streamId:{}", streamId);
			int ret = avformat.avformat_find_stream_info(inputFormatContext, (AVDictionary) null);
			if (ret < 0) 
			{
				logger.info("Cannot find the stream info for stream:{}", streamId);
				return null;
			}
			logger.info("Stream info is available");

			av_dump_format(inputFormatContext, 0, streamId, 0);

			muxAdaptor = MuxAdaptor.initializeMuxAdaptor(null, false, appAdaptor.getScope());

			boolean audioExist = false;
			boolean videoExist = false;
			for (int i = 0; i < inputFormatContext.nb_streams(); i++) 
			{
				if (inputFormatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) 
				{
					audioExist = true;
					if(avcodec.avcodec_find_decoder(inputFormatContext.streams(i).codecpar().codec_id()) == null) 
					{
						logger.error("avcodec_find_decoder() error: Unsupported audio format or codec not found");
						audioExist = false;
					}
				}
				else if (inputFormatContext.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) 
				{
					videoExist = true;
					if(avcodec.avcodec_find_decoder(inputFormatContext.streams(i).codecpar().codec_id()) == null) 
					{
						logger.error("avcodec_find_decoder() error: Unsupported video format or codec not found");
						videoExist = false;
					}
				}
			}

			muxAdaptor.setFirstKeyFrameReceivedChecked(!videoExist); 
			muxAdaptor.setEnableVideo(videoExist);
			muxAdaptor.setEnableAudio(audioExist);
			Broadcast broadcast = appAdaptor.getDataStore().get(streamId);
			muxAdaptor.setBroadcast(broadcast);
			//stream is mpegts so it's not AVC
			muxAdaptor.setAvc(false);

			MuxAdaptor.setUpEndPoints(muxAdaptor, broadcast, vertx);

			muxAdaptor.init(appAdaptor.getScope(), streamId, false);

			muxAdaptor.prepareFromInputFormatContext(inputFormatContext);

			pkt = avcodec.av_packet_alloc();

			prepared.set(true);

			logger.info("Prepared Zixi Client stream:{} and scope:{}", streamId, appAdaptor.getScope().getName());
			return inputFormatContext;
		} 
		catch (Exception e) 
		{
			logger.error(ExceptionUtils.getStackTrace(e));
		}

		return null;
	}
	
	public Result start() {
		
		if (connect()) {
			
			//cancel the timer
			readTimer = vertx.setPeriodic(10, h -> {
				
				byte[] data = new byte[BUFFER_SIZE];
				int[] writtenSize = new int[1];
				boolean[] isEOF = new boolean[1];
				boolean[] discontinuity = new boolean[1];
				int[] bitrate = new int[1];
				int ret;
				do {
					
					ret = zixi_read(zixiHandle, data, data.length, writtenSize, isEOF, discontinuity, false, bitrate);
					
					if (ret == ZIXI_ERROR_OK) 
					{					
						//add to queue
						byte[] readData = new byte[writtenSize[0]];
						System.arraycopy(data, 0, readData, 0, readData.length);
						queue.add(readData);
						lastReceivedPacketTimeMs = System.currentTimeMillis();

						if (prepared.get()) 
						{
							executeProcessPacket();
						}
					}
					else if (ret != ZIXI_ERROR_NOT_READY) {
						//it means there is a problem 
						logger.warn("Zixi error:{} for stream:{}", ret, streamUrl);
						stop();
					}
					
					if (lastReceivedPacketTimeMs != -1 && 
							(System.currentTimeMillis() - lastReceivedPacketTimeMs > INACTIVITY_TIMEOUT_MS))
					{
						logger.warn("No activity more than {} for Zixi Client with streamId:{} so stopping connection", INACTIVITY_TIMEOUT_MS, streamId);
						break;
					}
					
					if (isEOF[0]) {
						//end of file
						logger.info("End of file for zixi stream:{}", streamUrl);
						stop();
						break;
					}
				}
				while (ret == ZIXI_ERROR_OK);
			});

			return new Result(prepareContext() != null, streamId, "Stream pulling is started for " + streamUrl);			
		}
		else  {
			return new Result(false, "Cannot connect to URL");
		}
	}

	public AtomicBoolean getStopRequested() {
		return stopRequested;
	}
	
	public Result stop() {
		stopRequested.set(true);

		disconnect();

		if (readTimer != -1) {
			vertx.cancelTimer(readTimer);
			readTimer = -1;
		}

		vertx.executeBlocking(b -> 
		{
			//finish the queue
			while (!getQueue().isEmpty() && !stopped.get() && inputFormatContext != null) {
				//processPacket is internally calling itself but we just need to make sure that 
				processPacket(false);
			}


			if (isPipeReaderJobRunning.compareAndSet(false, true))  
			{
				logger.info("while stopping, number of items in the queue:{} streamId:{} and scope:{}", getQueue().size(), streamId, appAdaptor.getScope().getName());
				stopAndRelease(streamId, appAdaptor.getScope());
				isPipeReaderJobRunning.compareAndSet(true, false);
			}

			//stopped is set true and stream is stopped
			if (!stopped.get()) 
			{
				//if it's not stopped, try again in 100ms
				vertx.setTimer(100, l-> stop());
			}

		}, false, null);

		return new Result(true);
	}

	private synchronized void stopAndRelease(String streamId, IScope scope) {
		if (muxAdaptor != null) 
		{
			logger.info("Writing trailer in Muxadaptor {}", streamId);
			muxAdaptor.writeTrailer();
			appAdaptor.muxAdaptorRemoved(muxAdaptor);
			muxAdaptor = null;
		}

		releasePrepareContext();

		if (streamPublished && !stopped.get()) 
		{
			appAdaptor.closeBroadcast(streamId);
		}

		stopped.set(true);
	}


	private synchronized void releasePrepareContext() {

		logger.info("Releasing resources for zixi stream:{} and scope:{}", streamId, appAdaptor.getScope().getName());
		if (pkt != null) 
		{
			av_packet_free(pkt);
			pkt.close();
			pkt = null;
		}

		if (opaque != null) {
			socketQueueMap.remove(opaque);
			opaque.close();
			opaque = null;
		}

		if (inputFormatContext != null) 
		{
			logger.info("Releasing input format context:{} for stream:{} and scope:{} ",inputFormatContext, streamId, appAdaptor.getScope().getName());

			try {
				avformat_close_input(inputFormatContext);
			}
			catch (Exception e) {
				logger.info(e.getMessage());
			}
			inputFormatContext = null;
		}

		if (avioContext != null) {
			if (avioContext.buffer() != null) {
				av_free(avioContext.buffer());
				avioContext.buffer(null);
			}
			av_free(avioContext);
			avioContext = null;
		}
	}

	

	public Queue<byte[]> getQueue() {
		return queue;
	}


	private void processPacket(boolean callItSelfIfRequired) 
	{
		logger.trace("Calilng process packet for stream:{}", streamId);
		if (isPipeReaderJobRunning.compareAndSet(false, true))  
		{
			if (inputFormatContext == null) 
			{
				logger.warn("Zixi stream read is called after it's disconnected for stream:{} and scope:{}", streamId, appAdaptor.getScope().getName());
			}
			else 
			{
				if (av_read_frame(inputFormatContext, pkt) >= 0) 
				{
					if(!streamPublished) 
					{
						long currentTime = System.currentTimeMillis();
						muxAdaptor.setStartTime(currentTime);
						appAdaptor.startPublish(streamId, 0, ZixiPlugin.PUBLISH_TYPE_ZIXI_CLIENT);
					}

					streamPublished = true;
					muxAdaptor.writePacket(inputFormatContext.streams(pkt.stream_index()), pkt);
					av_packet_unref(pkt);
				}
				else 
				{
					logger.info("Cannot read packet for stream:{} and scope:{}", streamId, appAdaptor.getScope().getName());
				}
				
			}
			isPipeReaderJobRunning.compareAndSet(true, false);
		}
		else {
			logger.trace("By passing the processpacket because there is already a thread running inside for stream:{}", streamId);
		}
		
		if (callItSelfIfRequired && !getQueue().isEmpty() && !stopped.get() && inputFormatContext != null) 
		{
			//call execute process packet if not empty and not stopped and not inputFormatContext null
			logger.trace("execute process packet and queue size:{} for stream:{}", getQueue().size(), streamId);
			executeProcessPacket();
		}
	}

	public void executeProcessPacket() 
	{
		vertx.executeBlocking(k -> processPacket(true), false, null);
	}

	public AtomicBoolean getPrepared() {
		return prepared;
	}

	public static Map<Pointer, ZixiClient> getSocketqueuemap() {
		return socketQueueMap;
	}


}
