#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out build
javac -d out src/tubeforge/*.java
jar --create --file build/TubeForge.jar --main-class tubeforge.TubeForge -C out .
echo "Built: $(pwd)/build/TubeForge.jar"
