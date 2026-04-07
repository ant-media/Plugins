AMS_DIR=/home/usama/tem/newant/antmedia/
sudo ./install_dependencies.sh
sudo ./copy_python_files.sh
cp ./lib/sqlite-jdbc-*.jar  $AMS_DIR/lib/
cp ./jep-*.jar $AMS_DIR/lib/
cp ./*.xml $AMS_DIR
cd $AMS_DIR
python3 -m venv pythonAIPlugin
activate ./pythonAIPlugin/Scripts/activate
cd -
pip install requirement.txt
export PYTHONPATH=/home/usama/learn/AntMedia/HalkEkmekPlugin/ekmplug/lib/python3.14/site-packages/
