# TensorflowPlugin
This is a plugin project to detect and recognize objects in the stream with Tensorflow.

To use this plugin:
- Run the `install_tensorflow_plugin.sh` script in AMS installation directory. It will download dependant libraries
- copy the ant-media-tensorflow-plugin.jar into {AMS_INSTALLATION_DIRECTORY}/plugins
- Add an adaptive streaming settings from AMS Dashboard
- Start a stream
- Call the following Post REST method to start detection
```{SERVER_URL}/{APP_NAME}/rest/v2/tensorflow/{STREAM_ID}/start```
- You can find the detection results in `{AMS_INSTALLATION_DIRECTORY}/webapps/{APP_NAME}/previews`
- - Call the following Post REST method to stop detection
```{SERVER_URL}/{APP_NAME}/rest/v2/tensorflow/{STREAM_ID}/stop```


