# PythonWrapperPlugin

You can send the broadcast to your Python Script with this plugin. You can use this plugin to send the broadcast to your own Python Script and do whatever you want with the broadcast. For example, you can run object detection models in real time.

## How to Install

1. Download the pre-built `PythonWrapperApp.jar` file
  ```
  wget https://github.com/ant-media/Plugins/raw/master/PythonWrapperPlugin/build/PythonWrapperApp.jar
  ```
3. Copy the `PythonWrapperApp.jar` file to `plugins` directory under `/usr/local/antmedia`
  ```
  sudo cp PythonWrapperApp.jar /usr/local/antmedia/plugins
  ```
4. Restart the service
  ```
  sudo service antmedia restart
  ```
5. Copy your own Python Script into the Ant Media Server Plugins directory. You can use the example script in https://github.com/ant-media/Plugins/raw/master/PythonWrapperPlugin/sample/python_script.py
  ```
  sudo cp your_python_script.py /usr/local/antmedia/python_script.py
  ```
    
## How to Use

Python Wrapper Plugin have REST API to control the plugin.

* Start the Python Script

Call the REST Method below to let Ant Media Server send broadcast into Python script. You should pass streamId as query parameter you wanted to use as a parameter.
   ```
   curl -i -X POST -H "Accept: Application/json" "http://localhost:5080/WebRTCAppEE/rest/v1/python-wrapper-plugin/start"
   ```

* Stop the Python Script

Call the REST Method below to let Ant Media Server with the stream id you specified in the start method.
   ```
   curl -i -X POST -H "Accept: Application/json" "http://localhost:5080/WebRTCAppEE/rest/v1/python-wrapper-plugin/stop/{streamId}"
   ```



## How to Build from Source Code

- Clone the repository

  ```
  git clone https://github.com/ant-media/Plugins
  ```

- Go to the Python Wrapper Plugin directory

  ```
  cd Plugins/PythonWrapperPlugin
  ```

- Modify the redeploy.sh file with your Ant Media Server installation path

  ```
  Change AMS_DIR=/usr/local/antmedia/
  ```

- Build & install the plugin

  ```
  chmode +x redeploy.sh
  ./redeploy.sh
  ```

- Restart Ant Media Server

  ```
  sudo service antmedia restart
  ```

### How to Customize
You can modify the code and build the plugin by yourself to make it work with your own needs. For example, you can play the video or login to the web page with your own credentials before starting the broadcast.
Go to the PythonWrapperPlugin and modify the customModification method as you wish. Then build the plugin with the following command.

  ```
  chmode +x redeploy.sh
  ./redeploy.sh
  ```