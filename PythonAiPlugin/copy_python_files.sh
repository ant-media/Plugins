cp ./src/main/java/io/antmedia/app/*.py $AMS_DIR/
cp ./web/samples/publish_webrtc_vlm.html $AMS_DIR/webapps/LiveApp/samples
cp ./web/samples/watch_prompt_monitor.html $AMS_DIR/webapps/LiveApp/samples
cp ./web/samples/ollama_vision_queue_ui.html $AMS_DIR/webapps/LiveApp/samples
cd $AMS_DIR
./start.sh
