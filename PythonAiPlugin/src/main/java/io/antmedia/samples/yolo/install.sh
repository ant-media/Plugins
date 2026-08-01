AMS_DIR=/usr/local/antmedia
source $AMS_DIR/pythonAIPlugin/bin/activate
pip install -r ./requirements.txt
cp -r ../yolo/ $AMS_DIR/PythonPluginFiles/samples
