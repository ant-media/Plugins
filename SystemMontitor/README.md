# System Monitor Plugin

This plugin periodically checks and detects the RTSP streams assigned to an origin which is working currently. Then assign that RTSP streams to an alive cluster node ie. new origin.


---

## Build from Source

1. Clone the repository:

   ```bash
   git clone https://github.com/ant-media/Plugins.git
   ```

2. Navigate to the SystemMonitor directory:

   ```bash
   cd Plugins/SystemMonitor
   ```

3. Build the plugin:

   ```bash
   mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
   ```

4. Install the plugin:

   ```bash
   sudo cp target/SystemMonitor.jar /usr/local/antmedia/plugins
   ```

5. Restart the server:

   ```bash
   sudo service antmedia restart
   ```

---


For more information about the plugins, [visit this post](https://antmedia.io/plugins-will-make-ant-media-server-more-powerful/)
  
