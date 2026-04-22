#!/bin/bash
# 开发环境同步脚本：将 VS/WSL2 编译产物同步到当前 Java 项目以供测试

# 1. 定义源路径 (VS 项目在 WSL2 中的缓存路径)
VS_PROJECT_ROOT="/home/albalest/.vs/AlignmentCore"
VS_LIB_DIR="$VS_PROJECT_ROOT/lib"
VS_JAVA_SRC_DIR="$VS_PROJECT_ROOT/AlignmentCore/source/AlignmentCoreJava/Java" # 假设 JNI 生成的 Java 文件在此

# 2. 定义目标路径 (当前 Java 项目路径)
TARGET_LIB_DIR="./lib"
TARGET_JAVA_DIR="./AlignmentCore"

echo ">>> 正在同步开发环境产物..."

# 检查源目录是否存在
if [ ! -d "$VS_LIB_DIR" ]; then
    echo "ERROR: 源库目录 $VS_LIB_DIR 不存在，请先在 VS 中执行编译"
    exit 1
fi

# 3. 同步 .so 动态库
mkdir -p "$TARGET_LIB_DIR"
echo "--- 同步 .so 库文件 ---"
cp -av "$VS_LIB_DIR"/*.so* "$TARGET_LIB_DIR/"

# 4. 同步 .java 源文件 (AlignmentCore 文件夹)
if [ -d "$VS_JAVA_SRC_DIR" ]; then
    mkdir -p "$TARGET_JAVA_DIR"
    echo "--- 同步 .java 源文件 ---"
    cp -av "$VS_JAVA_SRC_DIR"/*.java "$TARGET_JAVA_DIR/"
else
    echo "WARN: 未找到 Java 源文件目录 $VS_JAVA_SRC_DIR，跳过 Java 同步"
fi

echo ">>> 同步完成！"
echo "提示：您可以现在运行 'javac' 或 'Run Main' 进行测试了。"
