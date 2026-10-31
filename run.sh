#!/bin/sh
# Builds (if needed) and runs Laser Tag using only the JDK - no Maven required.
# Usage: ./run.sh            start the game
#        ./run.sh --server   run a headless dedicated server (see README)
set -e
cd "$(dirname "$0")"
if [ ! -f build/lasertag.jar ] || [ -n "$(find src/main -newer build/lasertag.jar -name '*.java' | head -n 1)" ]; then
  rm -rf build/classes
  mkdir -p build/classes
  javac --release 17 -d build/classes $(find src/main/java -name '*.java')
  jar --create --file build/lasertag.jar --main-class lasertag.LaserTag -C build/classes .
fi
exec java -jar build/lasertag.jar "$@"
