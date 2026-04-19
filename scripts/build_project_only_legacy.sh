#!/bin/bash
set -euo pipefail

# 容器内脚本：只编译项目本体（不重编 Ipopt/依赖），并导出到 /output
# 依赖：/usr/local/aarch64-linux-gnu 已包含 libipopt.so 等（由宿主机缓存挂载进来）

HOST_ARCH=aarch64-linux-gnu
INSTALL_PREFIX=/usr/local/$HOST_ARCH
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}

need_cmd() {
  command -v "$1" >/dev/null 2>&1
}

if ! need_cmd ${HOST_ARCH}-gcc || ! need_cmd cmake || ! need_cmd ninja; then
  echo "ERROR: toolchain/cmake/ninja missing in image."
  echo "       Please rebuild third-party once: ./build_arm64_1804.sh --rebuild-thirdparty"
  exit 2
fi

if [ ! -f "$INSTALL_PREFIX/lib/libipopt.so" ] && [ ! -f "$INSTALL_PREFIX/lib/libipopt.so.3" ]; then
  echo "ERROR: libipopt not found under $INSTALL_PREFIX/lib (cache is empty)."
  echo "       Run once with third-party build:" 
  echo "       ./build_arm64_1804.sh --rebuild-thirdparty"
  exit 2
fi

apt-get update -y && apt-get install -y libeigen3-dev

echo ">>> Compiling Project (Cross, project-only)..."
rm -rf /source/out/build/linux-arm64
mkdir -p /source/out/build/linux-arm64
cd /source/out/build/linux-arm64

cmake -G Ninja \
  -DCMAKE_SYSTEM_NAME=Linux \
  -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_C_COMPILER=${HOST_ARCH}-gcc \
  -DCMAKE_CXX_COMPILER=${HOST_ARCH}-g++ \
  -DCMAKE_C_FLAGS="-pthread" \
  -DCMAKE_CXX_FLAGS="-pthread" \
  -DCMAKE_EXE_LINKER_FLAGS="-pthread" \
  -DCMAKE_FIND_ROOT_PATH=$INSTALL_PREFIX \
  -DCMAKE_PREFIX_PATH=$INSTALL_PREFIX \
  -DJAVA_HOME=$JAVA_HOME \
  -DJAVA_AWT_LIBRARY=NotNeeded -DJAVA_AWT_INCLUDE_PATH=NotNeeded -DJAVA_JVM_LIBRARY=NotNeeded -DJAVA_INCLUDE_PATH=$JAVA_HOME/include \
  -DJAVA_INCLUDE_PATH2=$JAVA_HOME/include/linux \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_RPATH="\$ORIGIN" \
  -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON \
  /source

ninja

echo ">>> Exporting artifacts to /output..."
mkdir -p /output

cp -P /source/lib/*.so /output/ 2>/dev/null || true

for dir in "$INSTALL_PREFIX/lib" "$INSTALL_PREFIX/lib64"; do
  if [ -d "$dir" ]; then
    cp -P "$dir"/libipopt.so* /output/ 2>/dev/null || true
    cp -P "$dir"/libadolc.so* /output/ 2>/dev/null || true
    cp -P "$dir"/libcoinhsl.so* /output/ 2>/dev/null || true
    cp -P "$dir"/libColPack.so* /output/ 2>/dev/null || true
  fi
done

cp -L /usr/lib/aarch64-linux-gnu/libblas.so.3 /output/ 2>/dev/null || true
cp -L /usr/lib/aarch64-linux-gnu/liblapack.so.3 /output/ 2>/dev/null || true
cp -P /usr/lib/aarch64-linux-gnu/libgfortran.so.4* /output/ 2>/dev/null || true
cp -P /usr/aarch64-linux-gnu/lib/libgcc_s.so.1 /output/ 2>/dev/null || true
cp -P /usr/aarch64-linux-gnu/lib/libgomp.so.1* /output/ 2>/dev/null || true

# Ensure BLAS/LAPACK runtime libs are included for target ARM machines.
if ! ls /output/libblas.so.3 /output/liblapack.so.3 >/dev/null 2>&1; then
  tmp_armlibs="/tmp/arm64_blas_lapack"
  rm -rf "$tmp_armlibs"
  mkdir -p "$tmp_armlibs"

  # Try apt first (works when mirror provides arm64 indexes).
  dpkg --add-architecture arm64 >/dev/null 2>&1 || true
  apt-get update -y >/dev/null 2>&1 || true
  (cd "$tmp_armlibs" && apt-get download libblas3:arm64 liblapack3:arm64 >/dev/null 2>&1 || true)

  # Fallback: fetch known Ubuntu bionic arm64 debs from ubuntu-ports.
  if ! ls "$tmp_armlibs"/*.deb >/dev/null 2>&1; then
    (cd "$tmp_armlibs" && \
      curl -fsSLO http://ports.ubuntu.com/ubuntu-ports/pool/main/l/lapack/libblas3_3.7.1-4ubuntu1_arm64.deb && \
      curl -fsSLO http://ports.ubuntu.com/ubuntu-ports/pool/main/l/lapack/liblapack3_3.7.1-4ubuntu1_arm64.deb) || true
  fi

  for deb in "$tmp_armlibs"/*.deb; do
    [ -f "$deb" ] || continue
    dpkg-deb -x "$deb" "$tmp_armlibs/extract" 2>/dev/null || true
  done

  cp -a "$tmp_armlibs"/extract/usr/lib/aarch64-linux-gnu/blas/libblas.so* /output/ 2>/dev/null || true
  cp -a "$tmp_armlibs"/extract/usr/lib/aarch64-linux-gnu/lapack/liblapack.so* /output/ 2>/dev/null || true
fi

if need_cmd patchelf; then
  for f in /output/libipopt.so* /output/libsipopt.so*; do
    [ -f "$f" ] || continue
    patchelf --set-rpath '\$ORIGIN' "$f" 2>/dev/null || true
  done
fi

echo ">>> Done (project-only)."
