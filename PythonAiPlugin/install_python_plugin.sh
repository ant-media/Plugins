AMS_DIR=/home/usama/tem/newant/antmedia/
sudo ./install_dependencies.sh
sudo ./copy_python_files.sh
cp ./lib/sqlite-jdbc-*.jar  $AMS_DIR/lib/
cp ./jep-*.jar $AMS_DIR/lib/
cp ./*.xml $AMS_DIR
export PYTHONPATH=/home/usama/learn/AntMedia/HalkEkmekPlugin/ekmplug/lib/python3.14/site-packages/
