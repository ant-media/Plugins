package io.antmedia.plugin;

public class ClipCreatorSettings {

    //10 minutes
    private int mp4CreationIntervalSeconds = 600;
    
    private boolean enabled = true;

    public int getMp4CreationIntervalSeconds() {
        return mp4CreationIntervalSeconds;
    }

    public void setMp4CreationIntervalSeconds(int mp4CreationIntervalSeconds) {
        this.mp4CreationIntervalSeconds = mp4CreationIntervalSeconds;
    }

	/**
	 * @return the enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * @param enabled the enabled to set
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	
}
