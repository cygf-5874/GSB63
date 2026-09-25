#!/usr/bin/env bash
# 跑既有用例 test/roarish/BitmapTest.java。起点应 12/12 全红（实现是空壳）。
set -euo pipefail

cd "$(dirname "$0")/.."

bash scripts/build.sh >/dev/null

java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
    -cp out roarish.BitmapTest
