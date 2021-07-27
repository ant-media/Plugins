package io.antmedia.filter;

public class MCUFilterConfiguration extends FilterConfiguration{
	protected String type = "mcu";
	private String overlay;
	private String layout;
	private int margin;
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getOverlay() {
		return overlay;
	}
	public void setOverlayId(String overlay) {
		this.overlay = overlay;
	}
	public String getLayout() {
		return layout;
	}
	public void setLayout(String layout) {
		this.layout = layout;
	}
	public int getMargin() {
		return margin;
	}
	public void setMargin(int margin) {
		this.margin = margin;
	}
}
