#!/bin/bash
set -euo pipefail

THIRD_PARTY_DIR="thirdPartySrc"
mkdir -p "$THIRD_PARTY_DIR"
cd "$THIRD_PARTY_DIR"

echo ">>> 开始准备第三方库源码..."

# 1. 解压 Ipopt
if [ -f "Ipopt-3.14.16.tar.gz" ]; then
    echo "解压 Ipopt..."
    rm -rf Ipopt
    mkdir -p Ipopt
    tar -xzf Ipopt-3.14.16.tar.gz -C Ipopt --strip-components=1
fi

# 2. 解压 ColPack
if [ -f "ColPack-1.0.10.tar.gz" ]; then
    echo "解压 ColPack..."
    rm -rf ColPack
    mkdir -p ColPack
    tar -xzf ColPack-1.0.10.tar.gz -C ColPack --strip-components=1
fi

# 3. 解压 ADOL-C
if [ -f "ADOL-C-2.7.2.tar.gz" ]; then
    echo "解压 ADOL-C..."
    rm -rf ADOL-C
    mkdir -p ADOL-C
    tar -xzf ADOL-C-2.7.2.tar.gz -C ADOL-C --strip-components=1
fi

# 4. 解压 CoinHSL
if [ -f "coinhsl.zip" ]; then
    echo "解压 CoinHSL..."
    rm -rf coinhsl
    unzip -q coinhsl.zip -d .
    # 确保文件夹名字是小写 coinhsl (脚本要求)
    if [ -d "coinhsl-archive" ]; then mv coinhsl-archive coinhsl; fi
fi

# 5. 处理 MUMPS
# 脚本需要的是 MUMPS_5.4.1.tar.gz 压缩包名
# 如果你的 ThirdParty-Mumps.zip 里包含这个 tar.gz，我们需要把它提取出来
if [ -f "ThirdParty-Mumps.zip" ]; then
    echo "处理 MUMPS..."
    unzip -q ThirdParty-Mumps.zip -d mumps_tmp
    find mumps_tmp -name "MUMPS_5.4.1.tar.gz" -exec cp {} . \;
    rm -rf mumps_tmp
fi

echo ">>> 第三方库源码准备就绪！"
ls -F
