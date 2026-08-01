AMS_DIR=/usr/local/antmedia
source $AMS_DIR/pythonAIPlugin/bin/activate
pip install -r ./requirement.txt
cp -r ../face/ $AMS_DIR/PythonPluginFiles/samples
