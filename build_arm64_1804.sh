#!/bin/bash
set -e

PROJECT_ROOT="/home/albalest/AlignmentCoreForJavaOnLinux"
OUT_DIR="$PROJECT_ROOT/out/linux-arm64-legacy"
DEPLOY_DIR="/mnt/e/ARDevelop/ALCoreForLinux/API/Linux_Arm_1804"
IMAGE="alignment-legacy-env:latest"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PACKAGE_NAME="ALCore_Linux_Arm64_1804_${TIMESTAMP}.tar.gz"

mkdir -p "$OUT_DIR"
mkdir -p "$DEPLOY_DIR"

echo ">>> Starting Docker Build..."
docker run --rm \
    -v "$PROJECT_ROOT":/source \
    -v "$OUT_DIR":/output \
    "$IMAGE" /bin/bash -c "
        set -e
        cd /source
        
        # 寻找真正的 CMakeLists
        CMAKE_PATH=\$(find . -name 'CMakeLists.txt' -not -path '*/out/*' | head -n 1)
        if [ -z \"\$CMAKE_PATH\" ]; then
            echo 'CRITICAL: No CMakeLists.txt found'
            exit 1
        fi
        
        SRC_DIR=\$(dirname \"\$CMAKE_PATH\")
        cd \"\$SRC_DIR\"
        
        # 修复递归
        sed -i 's/add_subdirectory(source)/# add_subdirectory(source)/g' CMakeLists.txt 2>/dev/null || true
        
        # 构建
        mkdir -p build_arm64
        cd build_arm64
        cmake -G Ninja \
            -DCMAKE_SYSTEM_NAME=Linux \
            -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
            -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
            -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
            -DCMAKE_FIND_ROOT_PATH=/usr/local/aarch64-linux-gnu \
            -DJAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
            -DCMAKE_BUILD_TYPE=Release \
            ..
        ninja
        
        # 清理旧产物并收集新产物
        rm -rf /output/*
        echo '>>> Collecting all .so files...'
        find . -name '*.so*' -exec cp -vP {} /output/ \;
        cp -vP /usr/local/aarch64-linux-gnu/lib/*.so* /output/ 2>/dev/null || true
    "

echo ">>> Packaging artifacts into $PACKAGE_NAME..."
cd "$OUT_DIR"
tar -czf "$DEPLOY_DIR/$PACKAGE_NAME" .

echo ">>> SUCCESS! Package is at: $DEPLOY_DIR/$PACKAGE_NAME"
ls -lh "$DEPLOY_DIR/$PACKAGE_NAME"
