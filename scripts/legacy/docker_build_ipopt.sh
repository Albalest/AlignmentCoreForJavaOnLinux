#!/bin/bash
set -e

# --- 按需安装缺失工具 (避免重建整个镜像) ---
echo ">>> Checking build dependencies..."
MISSING_PKGS=""

if ! command -v cmake &>/dev/null; then
    MISSING_PKGS="$MISSING_PKGS cmake"
fi

if ! command -v ninja &>/dev/null; then
    MISSING_PKGS="$MISSING_PKGS ninja-build"
fi

if ! dpkg -l | grep -q "libeigen3-dev"; then
    MISSING_PKGS="$MISSING_PKGS libeigen3-dev"
fi

if ! dpkg -l | grep -q "default-jdk-headless"; then
    MISSING_PKGS="$MISSING_PKGS default-jdk-headless"
fi

if [ -n "$MISSING_PKGS" ]; then
    echo ">>> Installing missing packages:$MISSING_PKGS"
    # Switch to Aliyun mirror for better connectivity in China
    sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list
    sed -i 's/ports.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list
    
    apt-get update -qq
    apt-get install -y --no-install-recommends $MISSING_PKGS
    echo ">>> Installation complete."
else
    echo ">>> All required tools already present."
fi

# Set Cross-Compiler Environment Variables
export CC=aarch64-linux-gnu-gcc
export CXX=aarch64-linux-gnu-g++
export FC=aarch64-linux-gnu-gfortran
export PKG_CONFIG_PATH=/usr/lib/aarch64-linux-gnu/pkgconfig
export CMAKE_GENERATOR=Ninja

if [ -f "/work/install_arm64/lib/libipopt.so" ] || [ -f "/work/install_arm64/lib/libipopt.so.3" ]; then
    echo ">>> Ipopt already present in /work/install_arm64; skipping rebuild."
else
    echo ">>> Preparing to build Ipopt..."
    # Ensure source is available
    if [ ! -d "build_ipopt/Ipopt_src" ]; then
        mkdir -p build_ipopt
        # Assuming /work/ipopt is mounted
        if [ -d "/work/ipopt" ]; then
            cp -r /work/ipopt build_ipopt/Ipopt_src
        else
            echo "ERROR: /work/ipopt not found. Cannot build Ipopt."
            exit 1
        fi
    fi
    
    cd build_ipopt/Ipopt_src
    
    # Create a clean build directory for the docker build
    if [ -d "build_docker" ]; then
        echo "Cleaning previous docker build..."
        rm -rf build_docker
    fi
    mkdir build_docker
    cd build_docker

    echo ">>> Configuring Ipopt for aarch64 (Ubuntu 18.04 compatible)..."
    # Note: We use system MUMPS (sequential) which is installed in the container
    ../configure \
        --host=aarch64-linux-gnu \
        --prefix=/work/install_arm64 \
        --disable-java \
        --with-lapack-lflags="-L/usr/lib/aarch64-linux-gnu -llapack -lblas" \
        --with-mumps-cflags="-I/usr/include/mumps_seq" \
        --with-mumps-lflags="-L/usr/lib/aarch64-linux-gnu -ldmumps_seq -lmumps_common_seq -lpord_seq" \
        --enable-static \
        --enable-shared

    echo ">>> Building Ipopt..."
    make -j$(nproc)

    echo ">>> Installing Ipopt..."
    make install
fi

echo ">>> Verification..."
# Verify the binary architecture and symbols
file /work/install_arm64/lib/libipopt.so
aarch64-linux-gnu-nm -D /work/install_arm64/lib/libipopt.so | grep -i Mumps || echo "SUCCESS: No undefined Mumps symbols!"

echo ">>> Building libCOptimizer.so (AlignmentCore) ..."
# Expect the C++ source to be mounted into the container at /cpp_src
# Default source path matches the user's WSL layout: /home/albalest/.vs/AlignmentCore
CPP_SRC_ROOT="${CPP_SRC_ROOT:-/cpp_src}"

if [ ! -d "$CPP_SRC_ROOT" ]; then
    echo "NOTE: CPP_SRC_ROOT ($CPP_SRC_ROOT) not found."
    echo "      Mount your source folder and re-run, e.g.:"
    echo "      docker run --rm -v $(pwd):/work -v /home/albalest/.vs/AlignmentCore:/cpp_src ipopt-cross-builder bash docker_build_ipopt.sh"
else
    # Ensure COIN include path compatibility: coin/ -> coin-or/
    if [ -d /work/install_arm64/include/coin-or ] && [ ! -e /work/install_arm64/include/coin ]; then
        ln -s coin-or /work/install_arm64/include/coin
    fi

    COPT_BUILD_DIR="/work/out/build/linux-arm64-legacy-coptimizer"
    rm -rf "$COPT_BUILD_DIR"
    mkdir -p "$COPT_BUILD_DIR"

    TOOLCHAIN_FILE="$COPT_BUILD_DIR/aarch64-toolchain.cmake"
    cat > "$TOOLCHAIN_FILE" <<'EOF'
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)

set(CMAKE_C_COMPILER aarch64-linux-gnu-gcc)
set(CMAKE_CXX_COMPILER aarch64-linux-gnu-g++)
set(CMAKE_Fortran_COMPILER aarch64-linux-gnu-gfortran)

# Prefer target libraries/headers
set(CMAKE_FIND_ROOT_PATH
    /work/install_arm64
    /usr/aarch64-linux-gnu
    /usr/lib/aarch64-linux-gnu
    /usr
)

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
EOF

    export CMAKE_INCLUDE_PATH="/work/install_arm64/include:/usr/include"
    export CMAKE_LIBRARY_PATH="/work/install_arm64/lib:/usr/lib/aarch64-linux-gnu"
    export PKG_CONFIG_PATH="/work/install_arm64/lib/pkgconfig:/usr/lib/aarch64-linux-gnu/pkgconfig"

    # CMake 3.10 doesn't support -S/-B syntax; use old-style invocation
    mkdir -p "$COPT_BUILD_DIR"
    cd "$COPT_BUILD_DIR"
    
    cmake "$CPP_SRC_ROOT" -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_PREFIX_PATH="/usr/share/eigen3/cmake;/work/install_arm64" \
        -DJAVA_AWT_LIBRARY=/usr/lib/jvm/java-17-openjdk-amd64/lib/server/libjvm.so \
        -DJAVA_JVM_LIBRARY=/usr/lib/jvm/java-17-openjdk-amd64/lib/server/libjvm.so \
        -DJAVA_INCLUDE_PATH=/usr/lib/jvm/java-17-openjdk-amd64/include \
        -DJAVA_INCLUDE_PATH2=/usr/lib/jvm/java-17-openjdk-amd64/include/linux \
        -DJAVA_AWT_INCLUDE_PATH=/usr/lib/jvm/java-17-openjdk-amd64/include \
        -DIPOPT_INCLUDE_DIR=/work/install_arm64/include \
        -DLIB_IPOPT=/work/install_arm64/lib/libipopt.so

    ninja COptimizer

    # Export the built library
    OUT_DIR="/work/out/linux-arm64-legacy"
    mkdir -p "$OUT_DIR" /work/lib_arm64 /work/lib

    if [ -f "$CPP_SRC_ROOT/lib/libCOptimizer.so" ]; then
        cp -P "$CPP_SRC_ROOT/lib/libCOptimizer.so" "$OUT_DIR/"
        cp -P "$CPP_SRC_ROOT/lib/libCOptimizer.so" /work/lib_arm64/
        cp -P "$CPP_SRC_ROOT/lib/libCOptimizer.so" /work/lib/
        echo ">>> Exported libCOptimizer.so to out/linux-arm64-legacy/, lib_arm64/, and lib/"
    else
        echo "ERROR: libCOptimizer.so not found at $CPP_SRC_ROOT/lib/libCOptimizer.so"
        echo "       Please check the AlignmentCore build outputs."
        exit 2
    fi
fi

echo ">>> Done! Artifacts are located in 'install_arm64' and 'out/linux-arm64-legacy' in your workspace."
