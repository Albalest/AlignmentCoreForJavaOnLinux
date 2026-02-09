#!/bin/bash
set -e

# Install dependencies
echo ">>> Installing Cross-Compilation Tools (Legacy - Ubuntu 18.04)..."

# Setup Multi-arch sources for Bionic (Using Aliyun Mirrors for speed)
dpkg --add-architecture arm64
cat > /etc/apt/sources.list <<END_SOURCES
deb [arch=amd64] http://mirrors.aliyun.com/ubuntu/ bionic main restricted universe multiverse
deb [arch=amd64] http://mirrors.aliyun.com/ubuntu/ bionic-updates main restricted universe multiverse
deb [arch=amd64] http://mirrors.aliyun.com/ubuntu/ bionic-security main restricted universe multiverse
deb [arch=arm64] http://mirrors.aliyun.com/ubuntu-ports/ bionic main restricted universe multiverse
deb [arch=arm64] http://mirrors.aliyun.com/ubuntu-ports/ bionic-updates main restricted universe multiverse
deb [arch=arm64] http://mirrors.aliyun.com/ubuntu-ports/ bionic-security main restricted universe multiverse
END_SOURCES

apt-get update || true
apt-get install -y build-essential cmake g++-aarch64-linux-gnu gfortran-aarch64-linux-gnu pkg-config unzip file liblapack-dev:arm64 libblas-dev:arm64 autoconf automake libtool ninja-build libeigen3-dev wget gawk sed patchelf

# Install newer CMake (3.20)
if [ ! -f "/opt/cmake-3.20.0-linux-x86_64/bin/cmake" ]; then
    echo ">>> Installing CMake 3.20..."
    if [ -f "/build/thirdPartySrc/cmake-3.20.0-linux-x86_64.tar.gz" ]; then
        echo "Using local CMake tarball..."
        cp /build/thirdPartySrc/cmake-3.20.0-linux-x86_64.tar.gz .
    else
        echo "Downloading CMake from Aliyun mirror..."
        wget -q https://mirrors.huaweicloud.com/cmake/v3.20.0/cmake-3.20.0-linux-x86_64.tar.gz || \
        wget -q https://github.com/Kitware/CMake/releases/download/v3.20.0/cmake-3.20.0-linux-x86_64.tar.gz
    fi
    tar -xzf cmake-3.20.0-linux-x86_64.tar.gz -C /opt
    rm -f cmake-3.20.0-linux-x86_64.tar.gz
fi
export PATH="/opt/cmake-3.20.0-linux-x86_64/bin:$PATH"

# Install JDK 11 (ARM64)
if [ ! -d "/opt/jdk-11" ]; then
    echo ">>> Installing JDK 11 (ARM64)..."
    if [ -f "/build/thirdPartySrc/jdk-11-arm64.tar.gz" ]; then
        echo "Using local JDK tarball..."
        mkdir -p /opt/jdk-11
        tar -xzf /build/thirdPartySrc/jdk-11-arm64.tar.gz -C /opt/jdk-11 --strip-components=1
    else
        echo "Downloading JDK 11 from Aliyun mirror..."
        wget -q https://mirrors.tuna.tsinghua.edu.cn/Adoptium/11/jdk/aarch64/linux/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.21_9.tar.gz -O jdk.tar.gz
        mkdir -p /opt/jdk-11
        tar -xzf jdk.tar.gz -C /opt/jdk-11 --strip-components=1
        rm jdk.tar.gz
    fi
fi

echo ">>> Debugging JDK Installation..."
{
    echo "Listing /opt/jdk-11:"
    ls -F /opt/jdk-11/ || echo "ls /opt/jdk-11 failed"
    echo "Finding jni.h:"
    find /opt/jdk-11 -name jni.h || echo "find jni.h failed"
} > /output/debug_jdk.txt

# Set Cross-Compiler Environment
export HOST_ARCH=aarch64-linux-gnu
export CC=$HOST_ARCH-gcc
export CXX=$HOST_ARCH-g++
export FC=$HOST_ARCH-gfortran
export AR=$HOST_ARCH-ar
export RANLIB=$HOST_ARCH-ranlib
export LD=$HOST_ARCH-ld
export JAVA_HOME=/opt/jdk-11

export INSTALL_PREFIX=/usr/local/$HOST_ARCH
mkdir -p $INSTALL_PREFIX

# Helper function
build_lib() {
    local SRC_DIR=$1
    local NAME=$2
    local CONFIGURE_ARGS=$3
    
    echo ">>> Building $NAME (Cross)..."
    rm -rf "/tmp/$NAME"
    cp -r "$SRC_DIR" "/tmp/$NAME"
    cd "/tmp/$NAME"
    
    # Only run autoreconf if configure is missing, to avoid breaking vendor scripts (like CoinHSL)
    if [ ! -f configure ] && ([ -f configure.ac ] || [ -f configure.in ]); then
        autoreconf -vif || true
    fi
    
    # Clean up
    rm -f config.cache
    find . -name "*.o" -delete
    find . -name "*.lo" -delete
    find . -name "*.la" -delete
    rm -rf .libs
    if [ -f Makefile ]; then
        make distclean || true
    fi
    
    ./configure --host=$HOST_ARCH --prefix=$INSTALL_PREFIX $CONFIGURE_ARGS
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

# 2.5 CoinHSL
if [ ! -f "$INSTALL_PREFIX/lib/libcoinhsl.so" ]; then
    build_lib "/build/thirdPartySrc/coinhsl" "CoinHSL" ""
else
    echo ">>> CoinHSL already built, skipping."
fi

# 2.6 MUMPS (Manual Build - Sequential)
if [ ! -f "$INSTALL_PREFIX/lib/libdmumps.a" ]; then
    echo ">>> Building MUMPS (Cross)..."
    rm -rf "/tmp/MUMPS"
    mkdir -p "/tmp/MUMPS"
    # Try multiple possible locations for the tarball
    MUMPS_TAR=""
    for p in "/build/thirdPartySrc/Ipopt/ThirdParty/Mumps/MUMPS_5.4.1.tar.gz" "/build/thirdPartySrc/MUMPS_5.4.1.tar.gz"; do
        if [ -f "$p" ]; then MUMPS_TAR="$p"; break; fi
    done
    
    if [ -z "$MUMPS_TAR" ]; then
        echo "ERROR: MUMPS_5.4.1.tar.gz not found!"
        exit 1
    fi
    
    tar -xzf "$MUMPS_TAR" -C "/tmp/MUMPS" --strip-components=1
    cd "/tmp/MUMPS"
    
    cp Make.inc/Makefile.inc.generic Makefile.inc
    
    # Edit Makefile.inc for cross-compilation and sequential build
    sed -i "s|^CC.*=.*|CC = $CC|" Makefile.inc
    sed -i "s|^FC.*=.*|FC = $FC|" Makefile.inc
    sed -i "s|^FL.*=.*|FL = $FC|" Makefile.inc
    sed -i "s|^AR.*=.*|AR = $AR rv |" Makefile.inc
    sed -i "s|^RANLIB.*=.*|RANLIB = $RANLIB|" Makefile.inc
    sed -i "s|^LAPACK.*=.*|LAPACK = -llapack -lblas|" Makefile.inc
    sed -i "s|^SCALAP.*=.*|SCALAP = |" Makefile.inc
    sed -i "s|^INCPAR.*=.*|INCPAR = -I../libseq|" Makefile.inc
    sed -i "s|^LIBPAR.*=.*|LIBPAR = \$(SCALAP) -L../libseq -lmpiseq|" Makefile.inc

    # Build position-independent code so Ipopt can link shared libipopt.so
    # (otherwise aarch64 ld fails with R_AARCH64_* relocations against non-PIC objects)
    sed -i -E "s/^(OPTF[[:space:]]*=).*/\1 -O -fPIC/" Makefile.inc || true
    sed -i -E "s/^(OPTL[[:space:]]*=).*/\1 -O -fPIC/" Makefile.inc || true
    sed -i -E "s/^(OPTC[[:space:]]*=).*/\1 -O -fPIC/" Makefile.inc || true


    
    # Build sequential version: build libraries only (skip examples)
    if [ -d src ]; then
        make -C src -j$(nproc)
    fi
    if [ -d libseq ]; then
        make -C libseq -j$(nproc)
        cp libseq/*.a ../libseq/ 2>/dev/null || true
    fi
    if [ -d PORD ]; then
        # Build PORD from its lib/ Makefile (some distributions put build rules in PORD/lib)
        if [ -f PORD/lib/Makefile ]; then
            make -C PORD/lib CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS='-g -O2 -fPIC' -j$(nproc) || true
            # copy resulting libpord into MUMPS lib directory for later linkage
            if [ -f PORD/lib/libpord.a ]; then
                mkdir -p lib
                cp PORD/lib/libpord.a lib/ 2>/dev/null || true
            fi
        else
            # fallback: try building PORD at top-level (some packages provide rules there)
            make -C PORD -j$(nproc) || true
        fi
    fi
    
    # Install
    mkdir -p $INSTALL_PREFIX/include/mumps
    cp include/*.h $INSTALL_PREFIX/include/mumps/
    # Also install libseq MPI headers for sequential MUMPS (Ipopt includes <mpi.h>)
    cp -f libseq/mpi.h libseq/mpif.h $INSTALL_PREFIX/include/mumps/ 2>/dev/null || true
    cp lib/*.a $INSTALL_PREFIX/lib/
    cp libseq/*.a $INSTALL_PREFIX/lib/
else
    echo ">>> MUMPS already built, skipping."
fi

# 3. Ipopt
# Force rebuild Ipopt to ensure MUMPS is linked correctly
echo ">>> Building Ipopt (Cross)..."
rm -rf "/tmp/Ipopt"
# Try multiple possible source locations for Ipopt (build environment may mount different paths)
IPOPT_SRC=""
for p in "/source/docker/thirdPartySrc/Ipopt" "/home/albalest/AlignmentTest/docker/thirdPartySrc/Ipopt" "/build/thirdPartySrc/Ipopt" "/source/thirdPartySrc/Ipopt" "/home/albalest/AlignmentTest/thirdPartySrc/Ipopt"; do
    if [ -d "$p" ]; then IPOPT_SRC="$p"; break; fi
done
if [ -z "$IPOPT_SRC" ]; then
    echo "ERROR: Ipopt source directory not found in known locations!"
    exit 1
fi
echo ">>> Using Ipopt source: $IPOPT_SRC"
cp -r "$IPOPT_SRC" "/tmp/Ipopt"
cd "/tmp/Ipopt"

# Clean any previous configuration in the source tree
find . -name "config.status" -delete
find . -name "Makefile" -delete
find . -name "config.cache" -delete
rm -rf build
mkdir -p build
cd build

# Ensure configure exists in the parent source directory; if not, try autoreconf there
if [ ! -f ../configure ] && ( [ -f ../configure.ac ] || [ -f ../configure.in ] ); then
    echo ">>> Running autoreconf in /tmp/Ipopt to generate configure script"
    cd ..
    autoreconf -vif || true
    cd build
fi

# Link with both HSL and MUMPS
# Note: We link against the static MUMPS libs we just built

# Ensure configure is executable (some mounts drop +x)
if [ -f ../configure ]; then
    chmod +x ../configure || true
fi

../configure --host=$HOST_ARCH --prefix=$INSTALL_PREFIX \
    --disable-java --disable-f77 \
    --with-mumps-cflags="-I$INSTALL_PREFIX/include/mumps" \
    --with-mumps-lflags="-L$INSTALL_PREFIX/lib -ldmumps -lmumps_common -lpord -lmpiseq" \
    --with-hsl-cflags="-I$INSTALL_PREFIX/include/coin-or/hsl -I$INSTALL_PREFIX/include" \
    --with-hsl-lflags="-L$INSTALL_PREFIX/lib -lcoinhsl -llapack -lblas" \
    --enable-static --enable-shared

make -j$(nproc)
make install

# Verify no undefined MUMPS symbols in the generated library
echo ">>> Verifying libipopt.so for undefined MUMPS symbols..."
if nm -D $INSTALL_PREFIX/lib/libipopt.so | grep "U " | grep -i Mumps; then
    echo "WARNING: libipopt.so still has undefined MUMPS symbols. Attempting to force re-compile without MumpsSolverInterface..."
    # If still present, it means the source files were already compiled with the flag.
    # We should have cleaned /tmp/Ipopt so this shouldn't happen.
fi

# Create symlink for backward compatibility (coin-or -> coin)
ln -sf "$INSTALL_PREFIX/include/coin-or" "$INSTALL_PREFIX/include/coin"

# If we only want to rebuild/export third-party libraries (e.g., Ipopt) and do NOT want to
# compile the main project mounted at /source, allow skipping that phase.
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

# 4. Project
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
    -DJNI_INCLUDE_PATH=$JAVA_HOME/include \
    -DJNI_INCLUDE_PATH2=$JAVA_HOME/include/linux \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_RPATH="\$ORIGIN" \
    -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON \
    /source

ninja

# Copy results to output
echo ">>> Exporting artifacts to /output..."
mkdir -p /output
cp -P /source/lib/*.so /output/
# Copy third-party libs from both lib and lib64
for dir in "$INSTALL_PREFIX/lib" "$INSTALL_PREFIX/lib64"; do
    if [ -d "$dir" ]; then
        cp -P "$dir"/libipopt.so* /output/ 2>/dev/null || true
        cp -P "$dir"/libadolc.so* /output/ 2>/dev/null || true
        cp -P "$dir"/libcoinhsl.so* /output/ 2>/dev/null || true
        cp -P "$dir"/libColPack.so* /output/ 2>/dev/null || true
    fi
done

# System libs
# Avoid exporting absolute /etc/alternatives symlinks; copy the resolved files instead.
cp -L /usr/lib/aarch64-linux-gnu/libblas.so.3 /output/
cp -L /usr/lib/aarch64-linux-gnu/liblapack.so.3 /output/
cp -P /usr/lib/aarch64-linux-gnu/libgfortran.so.4* /output/
cp -P /usr/aarch64-linux-gnu/lib/libgcc_s.so.1 /output/
cp -P /usr/aarch64-linux-gnu/lib/libgomp.so.1* /output/

# Ensure a self-contained drop: make Ipopt find adjacent libs via $ORIGIN.
if command -v patchelf >/dev/null 2>&1; then
    for f in /output/libipopt.so* /output/libsipopt.so*; do
        [ -f "$f" ] || continue
        patchelf --set-rpath '\$ORIGIN' "$f" 2>/dev/null || true
    done
fi
