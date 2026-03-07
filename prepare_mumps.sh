#!/bin/bash
set -euo pipefail

# 1. 尝试从华为镜像下载 MUMPS 5.4.1 (因为压缩包里没有，这是最稳妥的方法)
echo ">>> 正在从镜像站下载 MUMPS 5.4.1..."
cd thirdPartySrc
wget -q https://mirror.bazel.build/github.com/coin-or-tools/ThirdParty-Mumps/archive/refs/tags/releases/5.4.1.tar.gz -O MUMPS_5.4.1.tar.gz || \
wget -q https://github.com/coin-or-tools/ThirdParty-Mumps/archive/refs/tags/releases/5.4.1.tar.gz -O MUMPS_5.4.1.tar.gz

if [ -f "MUMPS_5.4.1.tar.gz" ]; then
    echo ">>> MUMPS 5.4.1 下载成功！"
    ls -l MUMPS_5.4.1.tar.gz
else
    echo ">>> 下载失败，请检查网络。"
    exit 1
fi
