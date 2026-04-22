#!/bin/bash
set -e
HOST_ARCH=aarch64-linux-gnu
INSTALL_PREFIX=/usr/local/$HOST_ARCH

# 自动寻找包含 CMakeLists.txt 的源目录
SOURCE_DIR=$(find /source -name "CMakeLists.txt" -not -path "*/build/*" -print -quit | xargs dirname)
echo ">>> Found Source Directory at: $SOURCE_DIR"

if [ -z "$SOURCE_DIR" ]; then
    echo "ERROR: CMakeLists.txt not found!"
    exit 1
fi

cd "$SOURCE_DIR"
# 修复递归问题
sed -i 's/add_subdirectory(source)/# add_subdirectory(source)/g' CMakeLists.txt

echo ">>> Compiling in: $SOURCE_DIR"
mkdir -p "$SOURCE_DIR/out/build/linux-arm64"
cd "$SOURCE_DIR/out/build/linux-arm64"

cmake -G Ninja \
  -DCMAKE_SYSTEM_NAME=Linux \
  -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_C_COMPILER=${HOST_ARCH}-gcc \
  -DCMAKE_CXX_COMPILER=${HOST_ARCH}-g++ \
  -DCMAKE_FIND_ROOT_PATH=$INSTALL_PREFIX \
  -DJAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  -DCMAKE_BUILD_TYPE=Release \
  "$SOURCE_DIR"

ninja clean || true
ninja

echo ">>> Collecting artifacts..."
mkdir -p /output
find . -name "*.so*" -exec cp -vP {} /output/ \;
cp -vP $INSTALL_PREFIX/lib/*.so* /output/ 2>/dev/null || true
