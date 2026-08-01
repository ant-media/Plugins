package io.antmedia.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginResultDatabase {

	private static final Logger logger = LoggerFactory.getLogger(PluginResultDatabase.class);
	private static final String DEFAULT_DB_PATH = "plugin_results.db";
	private static final Pattern VALID_TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

	private static PluginResultDatabase instance;
	private Connection connection;
	private final Set<String> createdTables = new HashSet<>();

	private PluginResultDatabase(String dbPath) {
		try {
			connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
			connection.setAutoCommit(true);
			logger.info("Plugin result database initialized at {}", dbPath);
		} catch (SQLException e) {
			logger.error("Failed to initialize SQLite database at {}", dbPath, e);
			throw new RuntimeException("Failed to initialize plugin result database", e);
		}
	}

	public static synchronized PluginResultDatabase getInstance() {
		if (instance == null) {
			instance = new PluginResultDatabase(DEFAULT_DB_PATH);
		}
		return instance;
	}

	public static synchronized PluginResultDatabase getInstance(String dbPath) {
		if (instance == null) {
			instance = new PluginResultDatabase(dbPath);
		}
		return instance;
	}

	private void validateTableName(String tableName) {
		if (tableName == null || !VALID_TABLE_NAME.matcher(tableName).matches()) {
			throw new IllegalArgumentException("Invalid table name: " + tableName);
		}
	}

	private boolean columnExists(String tableName, String columnName) {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
			while (rs.next()) {
				if (columnName.equalsIgnoreCase(rs.getString("name"))) {
					return true;
				}
			}
		} catch (SQLException e) {
			logger.error("PRAGMA table_info failed for {}", tableName, e);
		}
		return false;
	}

	private void ensureAppNameColumn(String tableName) {
		validateTableName(tableName);
		if (columnExists(tableName, "app_name")) {
			return;
		}
		String sql = "ALTER TABLE " + tableName + " ADD COLUMN app_name TEXT NOT NULL DEFAULT ''";
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
			logger.info("Added app_name column to table '{}'", tableName);
		} catch (SQLException e) {
			logger.error("Failed to add app_name column to '{}'", tableName, e);
		}
	}

	public synchronized void ensureTable(String tableName) {
		if (createdTables.contains(tableName)) {
			ensureAppNameColumn(tableName);
			return;
		}
		validateTableName(tableName);
		String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
				+ "id INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ "stream_id TEXT NOT NULL, "
				+ "json_data TEXT NOT NULL, "
				+ "app_name TEXT NOT NULL DEFAULT '', "
				+ "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
				+ ")";
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
			createdTables.add(tableName);
			logger.info("Ensured table '{}' exists", tableName);
		} catch (SQLException e) {
			logger.error("Failed to create table '{}'", tableName, e);
			throw new RuntimeException("Failed to create table: " + tableName, e);
		}
		ensureAppNameColumn(tableName);
	}

	public void insertResult(String tableName, String appName, String streamId, String jsonData) {
		ensureTable(tableName);
		String app = appName != null ? appName : "";
		String sql = "INSERT INTO " + tableName + " (stream_id, json_data, app_name) VALUES (?, ?, ?)";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, streamId);
			pstmt.setString(2, jsonData);
			pstmt.setString(3, app);
			pstmt.executeUpdate();
		} catch (SQLException e) {
			logger.error("Failed to insert result into '{}' app {} stream {}", tableName, app, streamId, e);
		}
	}

	public List<String> getResultsByStreamId(String tableName, String appName, String streamId, int limit) {
		ensureTable(tableName);
		String app = appName != null ? appName : "";
		String sql = "SELECT json_data FROM " + tableName
				+ " WHERE stream_id = ? AND app_name = ? ORDER BY id DESC LIMIT ?";
		List<String> results = new ArrayList<>();
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, streamId);
			pstmt.setString(2, app);
			pstmt.setInt(3, limit);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					results.add(rs.getString("json_data"));
				}
			}
		} catch (SQLException e) {
			logger.error("Failed to query results from '{}' app {} stream {}", tableName, app, streamId, e);
		}
		return results;
	}

	public List<String> getRecentResults(String tableName, int limit) {
		ensureTable(tableName);
		String sql = "SELECT json_data FROM " + tableName + " ORDER BY id DESC LIMIT ?";
		List<String> results = new ArrayList<>();
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setInt(1, limit);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					results.add(rs.getString("json_data"));
				}
			}
		} catch (SQLException e) {
			logger.error("Failed to query recent results from '{}'", tableName, e);
		}
		return results;
	}

	public List<String> getResultsByStreamIdAndRecentSeconds(String tableName, String appName, String streamId,
			int seconds) {
		ensureTable(tableName);
		String app = appName != null ? appName : "";
		String sql = "SELECT json_data FROM " + tableName
				+ " WHERE stream_id = ? AND app_name = ? AND datetime(created_at) >= datetime('now', ?)"
				+ " ORDER BY id ASC";
		List<String> results = new ArrayList<>();
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, streamId);
			pstmt.setString(2, app);
			pstmt.setString(3, "-" + seconds + " seconds");
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					results.add(rs.getString("json_data"));
				}
			}
		} catch (SQLException e) {
			logger.error(
					"Failed to query recent-seconds results from '{}' app {} stream {} seconds {}",
					tableName,
					app,
					streamId,
					seconds,
					e);
		}
		return results;
	}

	public void deleteByStreamId(String tableName, String appName, String streamId) {
		ensureTable(tableName);
		String app = appName != null ? appName : "";
		String sql = "DELETE FROM " + tableName + " WHERE stream_id = ? AND app_name = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, streamId);
			pstmt.setString(2, app);
			int deleted = pstmt.executeUpdate();
			logger.info("Deleted {} results from '{}' app {} stream {}", deleted, tableName, app, streamId);
		} catch (SQLException e) {
			logger.error("Failed to delete results from '{}' app {} stream {}", tableName, app, streamId, e);
		}
	}

	public void close() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
				logger.info("Plugin result database closed");
			}
		} catch (SQLException e) {
			logger.error("Error closing database", e);
		}
		instance = null;
	}
}
