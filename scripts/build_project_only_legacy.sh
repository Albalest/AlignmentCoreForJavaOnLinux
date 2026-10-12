#!/bin/bash
set -euo pipefail

# 容器内脚本：只编译项目本体（不重编 Ipopt/依赖），并导出到 /output
# 依赖：/usr/local/aarch64-linux-gnu 已包含 libipopt.so 等（由宿主机缓存挂载进来）

HOST_ARCH=aarch64-linux-gnu
INSTALL_PREFIX=/usr/local/$HOST_ARCH
# 镜像内只有 JDK 11（JNI 头文件与版本无关）
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64}

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

# --- 引导新版 CMake（source/CMakeLists.txt 使用 target_link_options，需要 >= 3.13）---
if ! cmake --version 2>/dev/null | grep -Eq "cmake version 3\.(1[3-9]|[2-9][0-9])|[4-9]\."; then
  echo ">>> System cmake too old; bootstrapping CMake 3.28.3 from /build/thirdPartySrc..."
  if [ -f /build/thirdPartySrc/cmake-3.28.3-linux-x86_64.sh ]; then
    mkdir -p /opt/cmake-3.28.3-linux-x86_64
    (cd /opt/cmake-3.28.3-linux-x86_64 && /build/thirdPartySrc/cmake-3.28.3-linux-x86_64.sh --skip-license) >/dev/null 2>&1 || true
    if [ -x /opt/cmake-3.28.3-linux-x86_64/bin/cmake ]; then
      export PATH=/opt/cmake-3.28.3-linux-x86_64/bin:$PATH
    fi
  fi
fi
echo ">>> Using CMake: $(cmake --version | head -1)"

# libeigen3-dev 已在镜像中；此处仅作兜底（网络不可用时不阻断）
apt-get update -y >/dev/null 2>&1 || true
apt-get install -y libeigen3-dev curl >/dev/null 2>&1 || true

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
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=65536 -Wl,-z,common-page-size=65536" \
  -DCMAKE_EXE_LINKER_FLAGS="-pthread -Wl,-z,max-page-size=65536 -Wl,-z,common-page-size=65536" \
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

# libipopt 可能在导出时从缓存复制回旧的 4KB 对齐版本；统一从静态库重链接。
if [ -f "$INSTALL_PREFIX/lib/libipopt.a" ] && [ -f /output/libipopt.so.3.14.16 ]; then
  ipopt_tmp=/output/.libipopt.so.3.14.16.relink
  aarch64-linux-gnu-g++ -shared \
    -Wl,-soname,libipopt.so.3 \
    -Wl,-z,max-page-size=65536 -Wl,-z,common-page-size=65536 \
    -o "$ipopt_tmp" \
    -Wl,--whole-archive "$INSTALL_PREFIX/lib/libipopt.a" -Wl,--no-whole-archive \
    -L"$INSTALL_PREFIX/lib" -ldmumps -lmumps_common -lpord -lmpiseq \
    -L/output -Wl,-l:liblapack.so.3 -Wl,-l:libblas.so.3 \
    -ldl -lpthread -lm
  mv -f "$ipopt_tmp" /output/libipopt.so.3.14.16
  echo ">>> Re-linked libipopt with 64KB page alignment."
fi

# libipopt/liblapack 依赖 Ubuntu 18.04 arm64 的 libgfortran.so.4。
if ! ls /output/libgfortran.so.4* >/dev/null 2>&1; then
  tmp_gfortran="/tmp/arm64_gfortran"
  rm -rf "$tmp_gfortran"
  mkdir -p "$tmp_gfortran"
  dpkg --add-architecture arm64 >/dev/null 2>&1 || true
  apt-get update -y >/dev/null 2>&1 || true
  (cd "$tmp_gfortran" && apt-get download libgfortran4:arm64 >/dev/null 2>&1 || true)
  for deb in "$tmp_gfortran"/*.deb; do
    [ -f "$deb" ] || continue
    dpkg-deb -x "$deb" "$tmp_gfortran/extract" 2>/dev/null || true
  done
  cp -a "$tmp_gfortran"/extract/usr/lib/aarch64-linux-gnu/libgfortran.so* /output/ 2>/dev/null || true
fi

if need_cmd patchelf; then
  for f in /output/libipopt.so* /output/libsipopt.so*; do
    [ -f "$f" ] || continue
    patchelf --set-rpath '\$ORIGIN' "$f" 2>/dev/null || true
  done
fi

echo ">>> Done (project-only)."
