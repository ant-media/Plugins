AMS_DIR=/usr/local/antmedia
source $AMS_DIR/pythonAIPlugin/bin/activate
pip install -r ./requirement.txt
cp -r ../olama/  $AMS_DIR/PythonPluginFiles/samples


cp -r ./web/* "$AMS_DIR/webapps/LiveApp/samples"
cp -r./web/* "$AMS_DIR/webapps/WebRTCAppEE/samples"
cp -r ./web/* "$AMS_DIR/webapps/live/samples"
