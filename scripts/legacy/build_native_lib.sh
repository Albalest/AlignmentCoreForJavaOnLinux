#!/bin/bash

# 脚本功能：编译 C++ 优化算法库并拷贝到 Java 项目目录
# 运行环境：WSL2 / Linux

# 1. 定义路径
# Java 项目根目录 (获取脚本所在目录的绝对路径)
JAVA_PROJECT_ROOT=$(dirname "$(readlink -f "$0")")
# C++ 源码目录 (根据之前的上下文，源码在 ~/.vs/AlignmentCore)
CPP_SOURCE_ROOT="$HOME/.vs/AlignmentCore"
# 构建目录
BUILD_DIR="$CPP_SOURCE_ROOT/out/build/linux-release"
# 目标库文件名
LIB_NAME="libCOptimizer.so"

echo "=== 开始构建流程 ==="
echo "Java项目路径: $JAVA_PROJECT_ROOT"
echo "C++源码路径:  $CPP_SOURCE_ROOT"
echo "构建输出路径: $BUILD_DIR"

# 2. 检查源码目录是否存在
if [ ! -d "$CPP_SOURCE_ROOT" ]; then
    echo "错误: 找不到 C++ 源码目录: $CPP_SOURCE_ROOT"
    exit 1
fi

# 3. 创建构建目录
if [ ! -d "$BUILD_DIR" ]; then
    echo "创建构建目录: $BUILD_DIR"
    mkdir -p "$BUILD_DIR"
fi

# 4. 运行 CMake 配置 (如果需要) 和 编译
cd "$BUILD_DIR"

echo "--- 正在运行 CMake 配置 ---"
# 注意：这里假设已经安装了 Ninja 和 CMake
cmake -G "Ninja" -DCMAKE_BUILD_TYPE=Release "$CPP_SOURCE_ROOT"

if [ $? -ne 0 ]; then
    echo "错误: CMake 配置失败"
    exit 1
fi

echo "--- 正在编译 (Ninja) ---"
ninja COptimizer

if [ $? -ne 0 ]; then
    echo "错误: 编译失败"
    exit 1
fi

# 5. 拷贝生成的库文件到 Java 项目的 lib 目录
# 根据 build.ninja 分析，输出文件位于源码根目录下的 lib 目录
SOURCE_LIB_PATH="$CPP_SOURCE_ROOT/lib/$LIB_NAME"
TARGET_LIB_PATH="$JAVA_PROJECT_ROOT/lib/$LIB_NAME"

echo "--- 正在拷贝库文件 ---"
if [ -f "$SOURCE_LIB_PATH" ]; then
    cp "$SOURCE_LIB_PATH" "$TARGET_LIB_PATH"
    echo "成功: 已将 $LIB_NAME 拷贝到 $TARGET_LIB_PATH"
else
    echo "错误: 在 $SOURCE_LIB_PATH 未找到库文件"
    # 尝试在构建目录查找作为备选
    ALT_PATH="$BUILD_DIR/$LIB_NAME"
    if [ -f "$ALT_PATH" ]; then
         cp "$ALT_PATH" "$TARGET_LIB_PATH"
         echo "成功: 已从构建目录拷贝 $LIB_NAME"
    else
         echo "错误: 无法找到编译生成的库文件"
         exit 1
    fi
fi

echo "=== 构建流程结束 ==="
