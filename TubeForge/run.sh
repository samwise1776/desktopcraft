#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f build/TubeForge.jar ]] || find src -type f -newer build/TubeForge.jar | grep -q .; then
  ./build.sh
fi
exec java -jar build/TubeForge.jar
