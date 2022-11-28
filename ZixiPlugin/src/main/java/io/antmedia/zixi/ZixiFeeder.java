package io.antmedia.zixi;

import org.apache.tika.utils.ExceptionUtils;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.ShortPointer;
import org.bytedeco.zixi.feeder.ZIXI_LOG_FUNC;
import org.bytedeco.zixi.feeder.zixi_stream_config;
import org.red5.server.api.scope.IScope;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


import io.antmedia.muxer.Muxer;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;
import static org.bytedeco.zixi.global.feeder.*;

import java.net.MalformedURLException;
import java.net.URL;

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

    private static ZIXI_LOG_FUNC loggerFunction = new ZIXI_LOG_FUNC() 
	{
		@Override
		public void call(org.bytedeco.javacpp.Pointer userData, int level, org.bytedeco.javacpp.BytePointer msg) {
			logger.info("zixi feeder log: {}", msg.getString());
			
		}
	};

    public ZixiFeeder(String url, Vertx vertx) {
        super(vertx);
        this.url = url;
        parseURL();
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
        return true;
    }

    @Override
    public AVFormatContext getOutputFormatContext() {
        return null;
    }

    public synchronized Result connect() {
        if (host == null || host.isEmpty() || 
                port == 0 || 
                channel == null || channel.isEmpty()) 
        {
            return new Result(false, "Zixi url parameter("+url+") has not been parsed successfully");
        }

        configureLogLevel(zixiLogLevel);

        int ret = zixi_configure_credentials(zixiUserCredential, zixiUserCredential.length(), "", 0);
        if (ret != ZIXI_ERROR_OK) {
            logger.warn("Zixi configure credentials has failed with return code:{}", ret);
            return new Result(false, "Zixi configure credentials has failed");
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
    
        return new Result(ret == ZIXI_ERROR_OK);
    }

    public synchronized boolean disconnect() {
        boolean result = false;
        if (zixiHandle != null) 
        {
            int ret = zixi_close_stream(zixiHandle);
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
}
