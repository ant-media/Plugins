package io.antmedia.plugin;

public class RtspOutSettings {

	/** Master switch. When false the plugin loads but does nothing. */
	private boolean enabled = true;

	/** Default for streams with no REST override. */
	private boolean enabledByDefault = true;

	/** Port MediaMTX serves RTSP on, and the port we publish to on loopback. */
	private int rtspPort = 8554;

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean v) { this.enabled = v; }

	public boolean isEnabledByDefault() { return enabledByDefault; }
	public void setEnabledByDefault(boolean v) { this.enabledByDefault = v; }

	public int getRtspPort() { return rtspPort; }
	public void setRtspPort(int v) { this.rtspPort = v; }
}
