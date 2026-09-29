package io.antmedia.plugin;

/** Basically a DTO.
 *  JSON MediaMTX posts to authHTTPAddress before it lets a connection publish or read. */
public class RtspAuthRequest {

	private String ip;
	private String user;
	private String password;
	private String token;

	/** publish, read, playback, api, metrics or pprof. */
	private String action;

	/** Always <app>/<streamId> or <app>/<streamId>_<height>p for us. */
	private String path;

	/** MediaMTX's own id. One reader gets a different one for DESCRIBE and SETUP, so it is no session key. */
	private String id;

	/** Raw query off the RTSP url, still percent encoded. */
	private String query;

	public String getIp() { return ip; }
	public void setIp(String v) { this.ip = v; }

	public String getUser() { return user; }
	public void setUser(String v) { this.user = v; }

	public String getPassword() { return password; }
	public void setPassword(String v) { this.password = v; }

	public String getToken() { return token; }
	public void setToken(String v) { this.token = v; }

	public String getAction() { return action; }
	public void setAction(String v) { this.action = v; }

	public String getPath() { return path; }
	public void setPath(String v) { this.path = v; }

	public String getId() { return id; }
	public void setId(String v) { this.id = v; }

	public String getQuery() { return query; }
	public void setQuery(String v) { this.query = v; }
}
