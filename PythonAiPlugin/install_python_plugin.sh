AMS_DIR=/usr/local/antmedia
sudo ./install_dependencies.sh

cp ./lib/*  $AMS_DIR/lib/
cp ./PythonAIPlugin.jar  $AMS_DIR/plugins 

cp ./*.xml $AMS_DIR

cd $AMS_DIR
python3 -m venv pythonAIPlugin
source ./pythonAIPlugin/bin/activate
cd -
pip install -r requirements.txt
cp ./python/* $AMS_DIR
export PYTHONPATH=$AMS_DIR/pythonAIPlugin/lib/python3.*/site-packages/
