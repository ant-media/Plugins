package io.antmedia.zixi;

import org.apache.tika.utils.ExceptionUtils;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avformat.Write_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avformat.Write_packet_Pointer_byte___int;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.ShortPointer;
import org.bytedeco.zixi.feeder.ZIXI_LOG_FUNC;
import org.bytedeco.zixi.feeder.zixi_stream_config;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


import io.antmedia.muxer.Muxer;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;
import static org.bytedeco.zixi.global.feeder.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;



import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZixiFeeder extends Muxer {

    protected static Logger logger = LoggerFactory.getLogger(ZixiFeeder.class);
    private String url;

    private String zixiUserCredential = "AntMediaServer";

    /*log detail level ,
		[-1] to turn off ,
		[0] to log everything (significantly hurt performance - only for deep debugging) ,
		[1-5] different log levels (3 recommended)
    */                  
    private int zixiLogLevel = 3;

    private int maxBitrateKbps = 10000;
    private int maxLatencyMs = 1000;
    private byte enforceBitrate = 0;
    private String host;
    private short port;
    private Pointer zixiHandle = null;
    //zixi channel id on the ZB. It's also aka stream id
    private String channel;
    private BytePointer opaque;
    private AVIOContext avioContext;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private BytePointer dataBuffer;

    //1316 is the max pkt size
    protected static final int BUFFER_SIZE = 1316;

    private static final Map<Pointer, ZixiFeeder> zixiFeederMap = new ConcurrentHashMap<>();

    private static ZIXI_LOG_FUNC loggerFunction = new ZIXI_LOG_FUNC() 
	{
		@Override
		public void call(org.bytedeco.javacpp.Pointer userData, int level, org.bytedeco.javacpp.BytePointer msg) {
			logger.info("zixi feeder log: {}", msg.getString());
			
		}
	};

    static Write_packet_Pointer_BytePointer_int writeCallback = new Write_packet_Pointer_BytePointer_int() {

        @Override
        public int call(Pointer opaque, BytePointer buf, int buf_size) {
            logger.debug("write callback is called with buf_size:{}", buf_size);

			ZixiFeeder zixiFeeder = zixiFeederMap.get(opaque);
            if (zixiFeeder.connected.get()) 
            {
                byte[] data = new byte[buf_size];
                buf.get(data, 0, buf_size);
                int ret = zixi_send_frame(zixiFeeder.zixiHandle, data, buf_size, (int)(System.currentTimeMillis()*90));

                if (ret == ZIXI_ERROR_NOT_READY)
                {
                    logger.info("Packet is too fast and rejected for zixi url:{} and stream id:{} ",
                                    zixiFeeder.url, zixiFeeder.streamId);
                }
                else {
                    logger.debug("Packet is written to zixi url->{} buf size:{}", zixiFeeder.url, buf_size);
                }


            }
            else {
                logger.warn("Zixi Feeder is not connected to the url:{} but it still try to send data", zixiFeeder.url);
            }
        
			return buf_size;

        }
    };


    public ZixiFeeder(String url, Vertx vertx) {
        super(vertx);
        this.url = url;
        parseURL();
        format = "mpegts";
    }

    public boolean parseURL() {
        boolean result = false;
        try 
        {
            if (url.startsWith("zixi:")) {
                //just a trick to make the URL object parse correctly
                String changedURL = url.replace("zixi://", "http://");

                URL tmpUrl = new URL(changedURL);
                host = tmpUrl.getHost();
                port = (short) tmpUrl.getPort();
                channel = tmpUrl.getPath().substring(1); //because first char is /
                result = true;
            }
            else {
                logger.warn("URL:{} is not a zixi URL ", url);
            }
        } 
        catch (MalformedURLException e) 
        {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
        return result;
    }

    public String getHost() {
        return host;
    }

    public short getPort() {
        return port;
    }

    public String getChannel() {
        return channel;
    }

    public boolean configureLogLevel(int logLevel)
    {
        int ret = zixi_configure_logging(logLevel, loggerFunction, null);
        if (ret == ZIXI_ERROR_OK) {
            logger.info("Zixi feeder logging configured successfully");
        }
        else {
            logger.warn("Zixi feeder logging configuration has failed with return code:{}", ret);
        }
        return ret == ZIXI_ERROR_OK;
    }

    @Override
    public boolean isCodecSupported(int codecId) {
        //TODO: check supported codecs
        return true;
    }

    @Override
    public boolean openIO() {
        boolean result = connect();
        if (result) {
            connected.set(true);
            opaque = new BytePointer(streamId);
            dataBuffer = new BytePointer(av_malloc(BUFFER_SIZE));
            
            zixiFeederMap.put(opaque, this);
            avioContext = avio_alloc_context(dataBuffer, BUFFER_SIZE, 1, 
                                opaque, null, writeCallback, null);

            getOutputFormatContext().pb(avioContext);

            int nofile = (outputFormatContext.flags() & AVFMT_NOFILE);
            logger.info("nofile:{} ", nofile);

            //outputFormatContext.o

            logger.info("Write callback method is created for zixi feeder stream:{} and zixi url:{}", streamId, url);
        }
        return result;
	}




    public synchronized boolean connect() {
        if (host == null || host.isEmpty() || 
                port == 0 || 
                channel == null || channel.isEmpty()) 
        {
            logger.warn("Zixi url parameter({}) has not been parsed successfully", url);
            return false;
        }

        configureLogLevel(zixiLogLevel);

        int ret = zixi_configure_credentials(zixiUserCredential, zixiUserCredential.length(), "", 0);
        if (ret != ZIXI_ERROR_OK) {
            logger.warn("Zixi configure credentials has failed with return code:{}", ret);
            return false;
        }

        zixi_stream_config cfg = new zixi_stream_config();
        
        cfg.enc_type(ZIXI_NO_ENCRYPTION);
        cfg.sz_enc_key(null);
        cfg.fast_connect((byte) 0);
        cfg.max_bitrate(maxBitrateKbps * 1000);
        cfg.max_latency_ms(maxLatencyMs);

        ShortPointer portPointer = new ShortPointer(1);
        portPointer.position(0).put((short) port);
        cfg.port(portPointer);
   
        BytePointer streamIdPointer = new BytePointer(channel);
        cfg.sz_stream_id(streamIdPointer); 
        cfg.stream_id_max_length(channel.length());

        PointerPointer<BytePointer> hosts = new PointerPointer<BytePointer>(1);
        BytePointer hostPointer = new BytePointer(host);
        hosts.put(0, hostPointer);
        cfg.sz_hosts(hosts);

        IntPointer hostsLengthPointer = new IntPointer(1);
        hostsLengthPointer.put(host.length());
        cfg.hosts_len(hostsLengthPointer);

        cfg.reconnect((byte) 1);
        cfg.num_hosts(1);
        cfg.use_compression((byte) 1);
        cfg.rtp((byte) 0);
        cfg.fec_overhead(15);
        cfg.content_aware_fec((byte) 0);
        cfg.fec_block_ms(30);
        cfg.timeout(0); 
        cfg.limited(ZIXI_ADAPTIVE_FEC);
        cfg.smoothing_latency(0);
        cfg.enforce_bitrate(enforceBitrate);
        cfg.force_bonding((byte) 0);
        cfg.protocol(ZIXI_PROTOCOL_UDP);
        

        PointerPointer<LongPointer> zixiHandlePointer = new PointerPointer<LongPointer>(1);
		zixiHandlePointer.position(0);

        ret = zixi_open_stream(cfg, null, zixiHandlePointer);
        if (ret == ZIXI_ERROR_OK) {
            logger.info("Zixi feeder opened the stream url:{}", url);
            zixiHandle = zixiHandlePointer.get(0);
        }
        else {
            logger.warn("Zixi feeder open stream has failed for url:{} ", url);
        }
    
        return ret == ZIXI_ERROR_OK;
    }

    public synchronized boolean disconnect() {
        boolean result = false;
        if (zixiHandle != null) 
        {
            int ret = zixi_close_stream(zixiHandle);
            connected.set(false);
            zixiHandle = null;
            if (ret == ZIXI_ERROR_OK) 
            {
                logger.info("Zixi close stream is successful for url: {}", url);
                result = true;
            }
            else 
            {
                logger.warn("Zixi close stream has failed for url: {}", url);
            }
        }
        return result;
    }

    @Override
	public synchronized boolean addStream(AVCodec codec, AVCodecContext codecContext, int streamIndex) {
		AVCodecParameters codecParameter = new AVCodecParameters();
		int ret = avcodec_parameters_from_context(codecParameter, codecContext);
		if (ret < 0) {
			logger.error("Cannot get codec parameters for {}", streamId);
		}
		
		//call super directly because no need to add bit stream filter 
		return super.addStream(codecParameter, codecContext.time_base(), streamIndex);
	}
	

	@Override
	public synchronized boolean addStream(AVCodecParameters codecParameters, AVRational timebase, int streamIndex) 
	{
		bsfVideoName = "h264_mp4toannexb";
		return super.addStream(codecParameters, timebase, streamIndex);
	}

    @Override
    public synchronized void writeTrailer() {
        logger.info("Calling writeTrailer buffer -> {}", outputFormatContext.pb().buf_ptr().isNull() );

        //Don't call super.writeTrailer for 2.5.1 and older version
        //super.writeTrailer();

		isRunning.set(false);
		av_write_trailer(outputFormatContext);

        logger.info("output format context is null -> {}", outputFormatContext.isNull());

        disconnect();

        if (avioContext != null) 
        {
            if (avioContext.buffer() != null) {
                av_free(avioContext.buffer());
                avioContext.buffer(null);
            }
            av_free(avioContext);
            avioContext = null;
            outputFormatContext.pb(null);
        }

        clearResource();

        if (opaque != null) 
        {
            zixiFeederMap.remove(opaque);
            opaque.close();
            opaque = null;
        }
    }

    public AVFormatContext getOutputFormatContext() {
		if (outputFormatContext == null) 
        {
			outputFormatContext = new AVFormatContext(null);
			int ret = avformat_alloc_output_context2(outputFormatContext, null, format, null);
			if (ret < 0) {
				logger.info("Could not create output context for {}",  getOutputURL());
				return null;
			}
		}
		return outputFormatContext;
	}
}
