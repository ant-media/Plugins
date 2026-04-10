#!/usr/bin/env bash
set -euo pipefail

AMS_DIR="${AMS_DIR:-/usr/local/antmedia}"
sudo ./install_dependencies.sh

cp ./lib/* "$AMS_DIR/lib/"
cp ./PythonAIPlugin.jar "$AMS_DIR/plugins"

cd "$AMS_DIR"
python3 -m venv pythonAIPlugin
source ./pythonAIPlugin/bin/activate
cd -
pip install -r requirements.txt
cp -r ./PythonPluginFiles "$AMS_DIR"

cp ./web/samples/* "$AMS_DIR/webapps/LiveApp/samples"
cp ./web/samples/* "$AMS_DIR/webapps/WebRTCAppEE/samples"
cp ./web/samples/* "$AMS_DIR/webapps/live/samples"

SITE_PACKAGES="$("$AMS_DIR/pythonAIPlugin/bin/python3" -c 'import sysconfig; print(sysconfig.get_path("purelib"))')"
export PYTHONPATH="$SITE_PACKAGES"

START_SH="$AMS_DIR/start.sh"
if [ -f "$START_SH" ]; then
  sed -i '\|export PYTHONPATH=.*pythonAIPlugin|d' "$START_SH"
  sed -i "1a export PYTHONPATH=\"$SITE_PACKAGES\"" "$START_SH"
fi


update_antmedia_service_pythonpath() {
  local f="$1"
  local site="$2"
  [ -f "$f" ] || return 0
  if ! grep -q '^\[Service\]' "$f"; then
    echo "install_python_plugin: no [Service] section in $f — skipping PYTHONPATH" >&2
    return 0
  fi
  local tmp
  tmp="$(mktemp)"
  sed '/^Environment=PYTHONPATH=/d' "$f" | awk -v p="$site" '
    /^\[Service\]/ { print; print "Environment=PYTHONPATH=" p; next }
    { print }
  ' > "$tmp"
  sudo mv "$tmp" "$f"
  echo "install_python_plugin: set Environment=PYTHONPATH in $f"
}

for SERVICE_FILE in "$AMS_DIR/antmedia.service" "/etc/systemd/system/antmedia.service"; do
  update_antmedia_service_pythonpath "$SERVICE_FILE" "$SITE_PACKAGES"
done

if command -v systemctl >/dev/null 2>&1; then
  sudo systemctl daemon-reload
  echo "install_python_plugin: ran systemctl daemon-reload"
  sudo systemctl restart antmedia
fi
