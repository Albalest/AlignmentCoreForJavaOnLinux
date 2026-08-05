#!/bin/bash
set -e

# ============================================================================
# compile_cross_legacy.sh
# Ubuntu 18.04 (Bionic) 容器内：交叉编译第三方库 + 项目本体，导出到 /output
#   - 第三方库安装前缀：/usr/local/aarch64-linux-gnu（宿主机 .cache 挂载持久化）
#   - 设置 SKIP_PROJECT_BUILD=1 时只编第三方并导出后退出
# ============================================================================

echo ">>> Installing Cross-Compilation Tools (Legacy - Ubuntu 18.04)..."

# --- 配置 Multi-arch 源（USTC 镜像）---
dpkg --add-architecture arm64
cat > /etc/apt/sources.list <<END_SOURCES
deb [arch=amd64] http://mirrors.ustc.edu.cn/ubuntu/ bionic main restricted universe multiverse
deb [arch=amd64] http://mirrors.ustc.edu.cn/ubuntu/ bionic-updates main restricted universe multiverse
deb [arch=amd64] http://mirrors.ustc.edu.cn/ubuntu/ bionic-security main restricted universe multiverse
deb [arch=arm64] http://mirrors.ustc.edu.cn/ubuntu-ports/ bionic main restricted universe multiverse
deb [arch=arm64] http://mirrors.ustc.edu.cn/ubuntu-ports/ bionic-updates main restricted universe multiverse
deb [arch=arm64] http://mirrors.ustc.edu.cn/ubuntu-ports/ bionic-security main restricted universe multiverse
END_SOURCES

apt-get update || true
apt-get install -y \
    build-essential cmake g++-aarch64-linux-gnu gfortran-aarch64-linux-gnu \
    pkg-config unzip file liblapack-dev:arm64 libblas-dev:arm64 \
    autoconf automake libtool ninja-build libeigen3-dev wget gawk sed patchelf \
    || true

# --- 使用镜像内已有的 JDK 11（JNI 头文件与版本无关）---
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
echo ">>> Using JAVA_HOME=$JAVA_HOME"
if [ ! -f "$JAVA_HOME/include/jni.h" ]; then
    echo "WARN: jni.h not found at $JAVA_HOME/include/jni.h"
fi

# --- 交叉编译环境变量 ---
export HOST_ARCH=aarch64-linux-gnu
export CC=$HOST_ARCH-gcc
export CXX=$HOST_ARCH-g++
export FC=$HOST_ARCH-gfortran
export AR=$HOST_ARCH-ar
export RANLIB=$HOST_ARCH-ranlib
export LD=$HOST_ARCH-ld

# 强制 64KB 页面对齐 + C++11 ABI（适配银河麒麟等目标系统）
export COMMON_FLAGS="-O3 -fPIC -Wl,-z,max-page-size=65536 -Wl,-z,common-page-size=65536 -D_GLIBCXX_USE_CXX11_ABI=1"
export CFLAGS="$COMMON_FLAGS"
export CXXFLAGS="$COMMON_FLAGS"
export LDFLAGS="-Wl,-z,max-page-size=65536 -Wl,-z,common-page-size=65536"

export INSTALL_PREFIX=/usr/local/$HOST_ARCH
mkdir -p "$INSTALL_PREFIX"

# --- 通用交叉编译辅助函数 ---
build_lib() {
    local SRC_DIR=$1
    local NAME=$2
    local CONFIGURE_ARGS=$3

    echo ">>> Building $NAME (Cross) with 64KB Alignment..."
    rm -rf "/tmp/$NAME"
    cp -r "$SRC_DIR" "/tmp/$NAME"
    cd "/tmp/$NAME"

    autoreconf -vif || true

    rm -f config.cache
    find . -name "*.o" -delete
    find . -name "*.lo" -delete
    find . -name "*.la" -delete
    rm -rf .libs
    if [ -f Makefile ]; then
        make distclean || true
    fi

    ./configure --host=$HOST_ARCH --prefix=$INSTALL_PREFIX $CONFIGURE_ARGS \
        CFLAGS="$CFLAGS" CXXFLAGS="$CXXFLAGS" LDFLAGS="$LDFLAGS"
    make -j$(nproc)
    make install
}

# 1. ColPack
if [ ! -f "$INSTALL_PREFIX/lib/libColPack.so" ]; then
    build_lib "/build/thirdPartySrc/ColPack" "ColPack" "--disable-openmp"
else
    echo ">>> ColPack already built, skipping."
fi

# 2. ADOL-C
if [ ! -f "$INSTALL_PREFIX/lib/libadolc.so" ]; then
    build_lib "/build/thirdPartySrc/ADOL-C" "ADOL-C" "--with-colpack=$INSTALL_PREFIX --enable-sparse ac_cv_func_malloc_0_nonnull=yes ac_cv_func_realloc_0_nonnull=yes"
else
    echo ">>> ADOL-C already built, skipping."
fi

# 2.5 CoinHSL（按需禁用，保持与历史一致）
#if [ ! -f "$INSTALL_PREFIX/lib/libcoinhsl.so" ]; then
#    build_lib "/build/thirdPartySrc/coinhsl" "CoinHSL" ""
#else
#    echo ">>> CoinHSL already built, skipping."
#fi

# 2.6 MUMPS (Sequential，交叉编译)
if [ ! -f "$INSTALL_PREFIX/lib/libdmumps.a" ]; then
    echo ">>> Building MUMPS (Cross)..."
    rm -rf "/tmp/MUMPS"
    mkdir -p "/tmp/MUMPS"
    MUMPS_TAR=""
    for p in "/build/thirdPartySrc/Ipopt/ThirdParty/Mumps/MUMPS_5.5.1.tar.gz" "/build/thirdPartySrc/MUMPS_5.5.1.tar.gz"; do
        if [ -f "$p" ]; then MUMPS_TAR="$p"; break; fi
    done

    if [ -z "$MUMPS_TAR" ]; then
        echo "ERROR: MUMPS_5.5.1.tar.gz not found!"
        exit 1
    fi

    tar -xzf "$MUMPS_TAR" -C "/tmp/MUMPS" --strip-components=1
    cd "/tmp/MUMPS"

    cp Make.inc/Makefile.inc.generic Makefile.inc

    sed -i "s|^CC.*=.*|CC = $CC|" Makefile.inc
    sed -i "s|^FC.*=.*|FC = $FC|" Makefile.inc
    sed -i "s|^FL.*=.*|FL = $FC|" Makefile.inc
    sed -i "s|^AR.*=.*|AR = $AR rv |" Makefile.inc
    sed -i "s|^RANLIB.*=.*|RANLIB = $RANLIB|" Makefile.inc
    sed -i "s|^LAPACK.*=.*|LAPACK = -llapack -lblas|" Makefile.inc
    sed -i "s|^SCALAP.*=.*|SCALAP = |" Makefile.inc
    sed -i "s|^INCPAR.*=.*|INCPAR = -I../libseq|" Makefile.inc
    sed -i "s|^LIBPAR.*=.*|LIBPAR = \$(SCALAP) -L../libseq -lmpiseq|" Makefile.inc

    # PIC 编译（否则 aarch64 ld 无法链接非 PIC 目标文件进共享库）
    sed -i -E "s/^(OPTF[[:space:]]*=).*/\1 -O -fPIC/" Makefile.inc || true
    sed -i -E "s/^(OPTL[[:space:]]*=).*/\1 -O -fPIC/" Makefile.inc || true
    sed -i -E "s/^(OPTC[[:space:]]*=).*/\1 -O -fPIC/" Makefile.inc || true

    if [ -d src ]; then
        make -C src -j$(nproc)
    fi
    if [ -d libseq ]; then
        make -C libseq -j$(nproc)
        cp libseq/*.a ../libseq/ 2>/dev/null || true
    fi
    if [ -d PORD ]; then
        if [ -f PORD/lib/Makefile ]; then
            make -C PORD/lib CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS='-g -O2 -fPIC' -j$(nproc) || true
            if [ -f PORD/lib/libpord.a ]; then
                mkdir -p lib
                cp PORD/lib/libpord.a lib/ 2>/dev/null || true
            fi
        else
            make -C PORD -j$(nproc) || true
        fi
    fi

    mkdir -p "$INSTALL_PREFIX/include/mumps"
    cp include/*.h "$INSTALL_PREFIX/include/mumps/"
    cp -f libseq/mpi.h libseq/mpif.h "$INSTALL_PREFIX/include/mumps/" 2>/dev/null || true
    cp lib/*.a "$INSTALL_PREFIX/lib/" 2>/dev/null || true
    cp libseq/*.a "$INSTALL_PREFIX/lib/" 2>/dev/null || true
    if [ -f PORD/lib/libpord.a ]; then
        cp PORD/lib/libpord.a "$INSTALL_PREFIX/lib/" 2>/dev/null || true
    fi
else
    echo ">>> MUMPS already built, skipping."
fi

# 3. Ipopt
echo ">>> Building Ipopt (Cross)..."
rm -rf "/tmp/Ipopt"
IPOPT_SRC=""
for p in "/build/thirdPartySrc/Ipopt" "/source/thirdPartySrc/Ipopt"; do
    if [ -d "$p" ]; then IPOPT_SRC="$p"; break; fi
done
if [ -z "$IPOPT_SRC" ]; then
    echo "ERROR: Ipopt source directory not found in known locations!"
    exit 1
fi
echo ">>> Using Ipopt source: $IPOPT_SRC"
cp -r "$IPOPT_SRC" "/tmp/Ipopt"
cd "/tmp/Ipopt"

find . -name "config.status" -delete
find . -name "Makefile" -delete
find . -name "config.cache" -delete
rm -rf build
mkdir -p build
cd build

if [ ! -f ../configure ] && ( [ -f ../configure.ac ] || [ -f ../configure.in ] ); then
    echo ">>> Running autoreconf in /tmp/Ipopt to generate configure script"
    cd ..
    autoreconf -vif || true
    cd build
fi

if [ -f ../configure ]; then
    chmod +x ../configure || true
fi

../configure --host=$HOST_ARCH --prefix=$INSTALL_PREFIX \
    --disable-java --disable-f77 \
    --with-mumps-cflags="-I$INSTALL_PREFIX/include/mumps" \
    --with-mumps-lflags="-L$INSTALL_PREFIX/lib -ldmumps -lmumps_common -lpord -lmpiseq -llapack -lblas" \
    --enable-static --enable-shared

make -j$(nproc)
make install

if nm -D "$INSTALL_PREFIX/lib/libipopt.so" | grep "U " | grep -i Mumps; then
    echo "WARNING: libipopt.so still has undefined MUMPS symbols."
fi

ln -sf "$INSTALL_PREFIX/include/coin-or" "$INSTALL_PREFIX/include/coin"

# --- 仅编译第三方时导出并退出 ---
if [ "${SKIP_PROJECT_BUILD:-0}" = "1" ]; then
    echo ">>> SKIP_PROJECT_BUILD=1: exporting third-party artifacts to /output and exiting"
    mkdir -p /output
    for dir in "$INSTALL_PREFIX/lib" "$INSTALL_PREFIX/lib64"; do
        if [ -d "$dir" ]; then
            cp -P "$dir"/libipopt.so* /output/ 2>/dev/null || true
            cp -P "$dir"/libsipopt.so* /output/ 2>/dev/null || true
            cp -P "$dir"/libadolc.so* /output/ 2>/dev/null || true
            cp -P "$dir"/libcoinhsl.so* /output/ 2>/dev/null || true
            cp -P "$dir"/libColPack.so* /output/ 2>/dev/null || true
        fi
    done
    exit 0
fi

# 4. 项目本体
echo ">>> Compiling Project (Cross)..."
rm -rf /source/out/build/linux-arm64
mkdir -p /source/out/build/linux-arm64
cd /source/out/build/linux-arm64
cmake -G Ninja \
    -DCMAKE_SYSTEM_NAME=Linux \
    -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
    -DCMAKE_C_COMPILER=$HOST_ARCH-gcc \
    -DCMAKE_CXX_COMPILER=$HOST_ARCH-g++ \
    -DCMAKE_FIND_ROOT_PATH=$INSTALL_PREFIX \
    -DCMAKE_PREFIX_PATH=$INSTALL_PREFIX \
    -DJAVA_HOME=$JAVA_HOME \
    -DJAVA_AWT_LIBRARY=NotNeeded -DJAVA_AWT_INCLUDE_PATH=NotNeeded -DJAVA_JVM_LIBRARY=NotNeeded \
    -DJAVA_INCLUDE_PATH=$JAVA_HOME/include \
    -DJAVA_INCLUDE_PATH2=$JAVA_HOME/include/linux \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_RPATH="\$ORIGIN" \
    -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON \
    /source

ninja

# 导出产物
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

# 系统运行库（arm64）
cp -L /usr/lib/aarch64-linux-gnu/libblas.so.3 /output/ 2>/dev/null || true
cp -L /usr/lib/aarch64-linux-gnu/liblapack.so.3 /output/ 2>/dev/null || true
cp -P /usr/lib/aarch64-linux-gnu/libgfortran.so.4* /output/ 2>/dev/null || true
cp -P /usr/aarch64-linux-gnu/lib/libgcc_s.so.1 /output/ 2>/dev/null || true
cp -P /usr/aarch64-linux-gnu/lib/libgomp.so.1* /output/ 2>/dev/null || true

# 让 Ipopt 通过 $ORIGIN 找到相邻库
if command -v patchelf >/dev/null 2>&1; then
    for f in /output/libipopt.so* /output/libsipopt.so*; do
        [ -f "$f" ] || continue
        patchelf --set-rpath '\$ORIGIN' "$f" 2>/dev/null || true
    done
fi

echo ">>> Done."
