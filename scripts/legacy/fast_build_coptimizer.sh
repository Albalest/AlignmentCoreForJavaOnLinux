#!/bin/bash
set -e

# --- 配置路径 ---
# 1. Windows 源代码路径 (WSL 挂载路径)
#    这是您在 Windows 下编辑代码的地方，直接挂载到容器中，省去 VS 同步步骤
WINDOWS_SOURCE_DIR="/mnt/d/ARDevelop/ALCoreForLinux/AlignmentCore"

# 2. Windows 部署路径
#    编译完成后，库文件会自动拷贝到这里
WINDOWS_DEPLOY_DIR="/mnt/d/ARDevelop/ALCoreForLinux/API"

# 3. 本地工作空间路径
WORKSPACE_DIR=$(pwd)
OUTPUT_DIR="$WORKSPACE_DIR/out/linux-arm64-legacy"

# 4. 依赖库路径 (Ipopt 等)
IPOPT_INCLUDE_DIR="$WORKSPACE_DIR/install_arm64/include" 
IPOPT_LIB_DIR="$WORKSPACE_DIR/install_arm64/lib"
ADOLC_INCLUDE_DIR="$WORKSPACE_DIR/thirdPartySrc/ADOL-C/ADOL-C/include"

mkdir -p "$OUTPUT_DIR"

echo ">>> 启动极速编译 (Direct Mount Mode)..."
echo "    Source: $WINDOWS_SOURCE_DIR"
echo "    Deploy: $WINDOWS_DEPLOY_DIR"

# 检查 Windows 路径是否存在
if [ ! -d "$WINDOWS_SOURCE_DIR" ]; then
    echo "Error: Windows source directory not found: $WINDOWS_SOURCE_DIR"
    exit 1
fi

# 启动 Docker 编译
# 注意：我们将 Windows 源码路径直接挂载为 /source
docker run --rm \
    -v "$WORKSPACE_DIR:/work" \
    -v "$WINDOWS_SOURCE_DIR:/source" \
    -v "$OUTPUT_DIR:/output" \
    -v "$IPOPT_LIB_DIR:/libs" \
    -v "$IPOPT_INCLUDE_DIR:/includes" \
    -v "$ADOLC_INCLUDE_DIR:/includes/adolc_src" \
    alignment-legacy-env \
    /bin/bash -c '
    set -e
    
    # 使用内存文件系统或容器内路径进行构建，避免在 NTFS 上产生大量中间文件 (提高速度)
    mkdir -p /work/out/build/fast-legacy
    cd /work/out/build/fast-legacy
    
    echo ">>> Running CMake..."
    # 使用新版 CMake (3.20+)
    cmake -G Ninja \
        -DCMAKE_SYSTEM_NAME=Linux \
        -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
        -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
        -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
        -DCMAKE_BUILD_TYPE=Release \
        -DIPOPT_INCLUDE_DIR="/includes;/includes/adolc_src" \
        -DLIB_IPOPT="/libs/libipopt.so" \
        -DJAVA_AWT_LIBRARY=/usr/lib/jvm/java-11-openjdk-amd64/lib/server/libjvm.so \
        -DJAVA_JVM_LIBRARY=/usr/lib/jvm/java-11-openjdk-amd64/lib/server/libjvm.so \
        -DJAVA_INCLUDE_PATH=/usr/lib/jvm/java-11-openjdk-amd64/include \
        -DJAVA_INCLUDE_PATH2=/usr/lib/jvm/java-11-openjdk-amd64/include/linux \
        -DJAVA_AWT_INCLUDE_PATH=/usr/lib/jvm/java-11-openjdk-amd64/include \
        -DCMAKE_INSTALL_RPATH="\$ORIGIN" \
        /source
    
    echo ">>> Compiling..."
    ninja COptimizer
    
    echo ">>> Copying artifacts..."
    # 尝试从源码目录的 lib 文件夹复制 (如果 CMake 配置为输出到那里)
    if [ -f "/source/lib/libCOptimizer.so" ]; then
        cp /source/lib/libCOptimizer.so /output/
    else
        # 否则在构建目录中查找
        find . -name "libCOptimizer.so" -exec cp {} /output/ \;
    fi
    '

echo ">>> Deploying to Windows..."
if [ -f "$OUTPUT_DIR/libCOptimizer.so" ]; then
    cp "$OUTPUT_DIR/libCOptimizer.so" "$WINDOWS_DEPLOY_DIR/"
    echo "SUCCESS: libCOptimizer.so deployed to $WINDOWS_DEPLOY_DIR"
else
    echo "ERROR: Build failed or output file not found."
    exit 1
fi
