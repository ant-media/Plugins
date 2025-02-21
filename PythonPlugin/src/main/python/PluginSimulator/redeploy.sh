set -e
cd .. 
export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig/
python3.13 setup.py build_ext --inplace
cd ./PluginSimulator/
make
export LD_LIBRARY_PATH=../
GST_DEBUG=3
./pluginsimulator
