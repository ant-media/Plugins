package io.antmedia.plugin;

public class RtspOutSettings {

	/** Master switch. When false the plugin loads but does nothing. */
	private boolean enabled = true;

	/** Default for streams with no REST override. */
	private boolean enabledByDefault = true;

	/** Run the app's play security on every RTSP reader. Off leaves the RTSP port wide open. */
	private boolean authEnabled = true;

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean v) { this.enabled = v; }

	public boolean isEnabledByDefault() { return enabledByDefault; }
	public void setEnabledByDefault(boolean v) { this.enabledByDefault = v; }

	public boolean isAuthEnabled() { return authEnabled; }
	public void setAuthEnabled(boolean v) { this.authEnabled = v; }
}
