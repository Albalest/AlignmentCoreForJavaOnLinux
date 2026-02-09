#!/bin/bash

# 1. 定义路径
WIN_SRC_PATH="/mnt/c/Users/albal/thirdParty/Ipopt"
LINUX_DEST_PATH="./docker/thirdPartySrc"

# 2. 清理旧环境
echo ">>> [1/5] 清理旧的构建目录..."
rm -rf "$LINUX_DEST_PATH"
mkdir -p "$LINUX_DEST_PATH"

# 3. 复制文件
echo ">>> [2/5] 从 Windows 复制源码..."
if [ ! -d "$WIN_SRC_PATH" ]; then
    echo "错误: 找不到源路径 $WIN_SRC_PATH"
    exit 1
fi

# 3.1 复制 Ipopt
echo "--- 复制 Ipopt ---"
cp -r "$WIN_SRC_PATH/Ipopt" "$LINUX_DEST_PATH/"

# 3.2 复制 ADOL-C
echo "--- 复制 ADOL-C ---"
cp -r "$WIN_SRC_PATH/ADOL-C" "$LINUX_DEST_PATH/"

# 3.3 复制 CoinHSL (从 ThirdParty-HSL)
echo "--- 复制 CoinHSL ---"
cp -r "$WIN_SRC_PATH/ThirdParty-HSL" "$LINUX_DEST_PATH/coinhsl"

# 3.4 解压 ColPack
echo "--- 解压 ColPack ---"
tar -xzf "$WIN_SRC_PATH/colpack_1.0.10.orig.tar.gz" -C "$LINUX_DEST_PATH/"
# 解压后可能是 ColPack-1.0.10 或类似名称，需要重命名为 ColPack
# 检查解压后的目录名
EXTRACTED_DIR=$(ls "$LINUX_DEST_PATH" | grep -i "ColPack" | head -n 1)
if [ -n "$EXTRACTED_DIR" ] && [ "$EXTRACTED_DIR" != "ColPack" ]; then
    mv "$LINUX_DEST_PATH/$EXTRACTED_DIR" "$LINUX_DEST_PATH/ColPack"
fi

echo ">>> 源码准备完成!"
ls -F "$LINUX_DEST_PATH"
