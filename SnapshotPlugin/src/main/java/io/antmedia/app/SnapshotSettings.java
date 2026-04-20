package io.antmedia.app;

public class SnapshotSettings {
	/**
	 * Enables automatic snapshots at regular intervals.
	 */
	private boolean autoSnapshotEnabled = false;

	/**
	 * Target width for the snapshot. 
	 * If 0, the source width is used. 
	 * If greater than 0 but less than source width, image is downscaled. 
	 * Upscaling is not supported; source width is used if target > source.
	 */
	private int resolutionWidth = 0;

	/**
	 * Target height for the snapshot.
	 * If 0, the source height is used.
	 * If greater than 0 but less than source height, image is downscaled.
	 * Upscaling is not supported; source height is used if target > source.
	 */
	private int resolutionHeight = 0;

	/**
	 * Interval in seconds between automatic snapshots.
	 * Default is 60 seconds.
	 */
	private int intervalSeconds = 60;

	public boolean isAutoSnapshotEnabled() {
		return autoSnapshotEnabled;
	}

	public void setAutoSnapshotEnabled(boolean autoSnapshotEnabled) {
		this.autoSnapshotEnabled = autoSnapshotEnabled;
	}

	public int getResolutionWidth() {
		return resolutionWidth;
	}

	public void setResolutionWidth(int resolutionWidth) {
		this.resolutionWidth = resolutionWidth;
	}

	public int getResolutionHeight() {
		return resolutionHeight;
	}

	public void setResolutionHeight(int resolutionHeight) {
		this.resolutionHeight = resolutionHeight;
	}

	public int getIntervalSeconds() {
		return intervalSeconds;
	}

	public void setIntervalSeconds(int intervalSeconds) {
		this.intervalSeconds = intervalSeconds;
	}
}
