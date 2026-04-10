#!/bin/sh
set -e

# Build output folder can be overridden:
#   DIST_DIR=./dist-custom ./make_dist.sh
DIST_DIR="${DIST_DIR:-./dist}"

echo "Building plugin jar..."
mvn clean package -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true

echo "Preparing distribution directory: $DIST_DIR"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/lib" "$DIST_DIR/python"

echo "Copying plugin jar..."
cp ./target/PythonAIPlugin.jar "$DIST_DIR/"

echo "Copying sqlite JDBC jar..."
cp ./target/lib/sqlite-jdbc-*.jar "$DIST_DIR/lib/"

echo "Copying JEP jar..."
JEP_JAR=""
if [ -n "$PYTHONPATH" ]; then
  JEP_JAR=$(find "$PYTHONPATH" -name 'jep-*.jar' -print -quit 2>/dev/null || true)
fi
if [ -z "$JEP_JAR" ]; then
  JEP_JAR=$(find ./target/lib -name 'jep-*.jar' -print -quit 2>/dev/null || true)
fi
if [ -n "$JEP_JAR" ]; then
  cp "$JEP_JAR" "$DIST_DIR/lib/"
else
  echo "WARNING: Could not find jep-*.jar in PYTHONPATH or target/lib"
fi

echo "Copying Python files..."

mkdir ./dist/PythonPluginFiles
cp -r ./src/main/java/io/antmedia/samples  "dist/PythonPluginFiles"
cp -r ./src/main/java/io/antmedia/samples/init_plugins.py  "dist/PythonPluginFiles"
cp ./src/main/java/io/antmedia/app/*.py   "dist/PythonPluginFiles"

cp -r ./web/ ./dist/

echo "Distribution created at: $DIST_DIR"
ls -la "$DIST_DIR"

cp ./install_dependencies.sh ./dist/
cp ./install_python_plugin.sh ./dist/
cp ./requirements.txt ./dist/
