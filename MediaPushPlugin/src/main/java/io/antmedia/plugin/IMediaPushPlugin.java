package io.antmedia.plugin;

import io.antmedia.model.Endpoint;
import io.antmedia.rest.model.Result;

/**
 * MediaPush Plugin Interface
 * If you need to change the interface, make sure that it's backward compatible because
 * this interface is being used some other plugins through Reflection
 * 
 * @author mekya
 *
 */
public interface IMediaPushPlugin {
	
	static String BEAN_NAME = "plugin.mediaPushPlugin";
	
	/**
	 * Start media push with primitive arguments that is easy to call reflection
	 * @param streamId: random generated stream id
	 * @param websocketUrl:  websocket endpoint of the application 
	 * @param width
	 * @param height
	 * @param url: The url to publish to Ant Media Server
	 * @param token: Token to access to the publisherJs URL
	 * @param recordTypeString: If recording is required, type of the recording. It can be mp4 or webm. It works according to the codec configuration on server
	 * @return
	 */
	public Result startMediaPush(String streamId, String websocketUrl,
			int width, int height, String url, String token, String recordTypeString);
	
	
	/**
	 * Start media push directly {@link Endpoint}
	 * @param endpoint
	 * @return
	 */
	public Result startMediaPush(String streamIdPar, String websocketUrl, Endpoint endpoint);
	
	/**
	 * 
	 * @param streamId
	 * @return
	 */
	public Result stopMediaPush(String streamId);

}
