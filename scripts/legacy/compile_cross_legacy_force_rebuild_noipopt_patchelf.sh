#!/bin/bash
set -e

echo ">>> Force Rebuild Implementation..."
HOST_ARCH=aarch64-linux-gnu
INSTALL_PREFIX=/usr/local/$HOST_ARCH
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64}

# 1. 修复 CMakeLists
cd /source
sed -i 's/add_subdirectory(source)/# add_subdirectory(source)/g' CMakeLists.txt

# 2. 编译项目 (强制)
mkdir -p out/build/linux-arm64
cd out/build/linux-arm64
cmake -G Ninja \
  -DCMAKE_SYSTEM_NAME=Linux \
  -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_C_COMPILER=${HOST_ARCH}-gcc \
  -DCMAKE_CXX_COMPILER=${HOST_ARCH}-g++ \
  -DCMAKE_FIND_ROOT_PATH=$INSTALL_PREFIX \
  -DCMAKE_PREFIX_PATH=$INSTALL_PREFIX \
  -DJAVA_HOME=$JAVA_HOME \
  -DCMAKE_BUILD_TYPE=Release \
  /source
ninja clean || true
ninja

# 3. 收集产物
mkdir -p /output
find . -name "*.so*" -exec cp -vP {} /output/ \;
