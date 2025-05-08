set -e
cd .. 
python3 setup.py build_ext --inplace
cd ./PluginSimulator/
make
export LD_LIBRARY_PATH=../
GST_DEBUG=3
./pluginsimulator
