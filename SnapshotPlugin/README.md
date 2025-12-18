# Ant Media Server Snapshot Plugin

A simple and efficient plugin for Ant Media Server to capture video frame snapshots on-demand or automatically (time-lapse).

## Features
- **On-Demand Snapshots**: Trigger a snapshot instantly via REST API.
- **Auto Time-Lapse**: Schedule automatic snapshots at configurable intervals.
- **4K Ready**: Captures full resolution; supports downscaling (no upscaling).
- **Formats**: Supports JPG
- **Zero-Block**: Uses async processing to ensure stream performance is unaffected.

## Configuration
The plugin is configured via the application settings (AppSettings). You can add a custom setting with the key `plugin.snapshot-plugin` containing a JSON object with the following parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `autoSnapshotEnabled` | boolean | `false` | Enables automatic snapshots at regular intervals. |
| `resolutionWidth` | int | `0` | Target width. If `0`, source width is used. Only downscaling is supported. |
| `resolutionHeight` | int | `0` | Target height. If `0`, source height is used. Only downscaling is supported. |
| `intervalSeconds` | int | `60` | Interval in seconds between automatic snapshots. |

### Example Configuration
```json
{
  "customSettings": {
    "plugin.snapshot-plugin": {
        "autoSnapshotEnabled": true,
        "intervalSeconds": 10,
        "resolutionWidth": 1280,
        "resolutionHeight": 720
    }
 }
}
```

## Installation
1. Build the project: `mvn clean install`
2. Copy `target/SnapshotPlugin.jar` to `/usr/local/antmedia/plugins/`
3. Restart Ant Media Server.

## API Usage

### 1. Take Manual Snapshot
Capture a single frame immediately using global settings.

**POST** `http://ams-host/AppName/rest/snapshot-plugin/snapshot/{streamId}`

### 2. Start Time-Lapse
Enable automatic snapshots for a specific stream using global settings.

**POST** `http://ams-host/AppName/rest/snapshot-plugin/timelapse/start/{streamId}`

### 3. Stop Time-Lapse
Stop the automatic snapshot task for a specific stream.

**POST** `http://ams-host/AppName/rest/snapshot-plugin/timelapse/stop/{streamId}`

### 4. Check Time-Lapse Status
Check if the time-lapse is active for a specific stream.

**GET** `http://ams-host/AppName/rest/snapshot-plugin/timelapse/status/{streamId}`

**Response:**
```json
{
  "success": true,
  "status": "active" 
}
```
(or "inactive")

## Output Location
Snapshots are saved in the application's stream directory:
`webapps/{AppName}/streams/snapshots/{streamId}/`

**Filenames:**
- Manual: `manual_{height}_{timestamp}.{ext}`
- Auto: `auto_{height}_{timestamp}.{ext}`
