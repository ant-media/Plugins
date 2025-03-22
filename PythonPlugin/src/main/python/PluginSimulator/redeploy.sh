set -e
cd .. 
sudo cp ./python_plugin.py /usr/local/antmedia
python3 setup.py build_ext --inplace
cd ./PluginSimulator/
make
export LD_LIBRARY_PATH=../
GST_DEBUG=3
./pluginsimulator
