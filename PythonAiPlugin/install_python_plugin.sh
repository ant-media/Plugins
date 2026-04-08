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

SITE_PACKAGES="$("$AMS_DIR/pythonAIPlugin/bin/python3" -c 'import sysconfig; print(sysconfig.get_path("purelib"))')"
export PYTHONPATH="$SITE_PACKAGES"

START_SH="$AMS_DIR/start.sh"
if [ -f "$START_SH" ]; then
  sed -i '\|export PYTHONPATH=.*pythonAIPlugin|d' "$START_SH"
  sed -i "1a export PYTHONPATH=\"$SITE_PACKAGES\"" "$START_SH"
fi
