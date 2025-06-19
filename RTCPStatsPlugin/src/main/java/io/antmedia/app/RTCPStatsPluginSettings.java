package io.antmedia.app;

public class RTCPStatsPluginSettings {
	/**
	 * If set to TRUE, updated will be sent on every new Status Report package
	 * If false, it will be sent every frame
	 */
	private boolean updateOnlyOnNewSR = true;

	public boolean isUpdateOnlyOnNewSR() {
		return updateOnlyOnNewSR;
	}

	public void setUpdateOnlyOnNewSR(boolean updateOnlyOnNewSR) {
		this.updateOnlyOnNewSR = updateOnlyOnNewSR;
	}
}
