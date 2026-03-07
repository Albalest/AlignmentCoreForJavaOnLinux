# Ipopt 及其依赖库 Linux 编译安装指南

本文档记录了基于现有源码包在 Linux 环境下完整编译 Ipopt (包含 sIpopt) 及其核心依赖（ColPack, ADOL-C, MUMPS）的流程。

## 1. 编译环境准备

在开始之前，确保已安装必要的构建工具和基础库：

```bash
sudo apt update
sudo apt install -y build-essential g++ gfortran git cmake ninja-build \
                    autoconf automake libtool pkg-config \
                    liblapack-dev libblas-dev libeigen3-dev default-jdk
```

## 2. 编译流程

所有库默认安装到 `/usr/local` 路径。

### 2.1 编译 ColPack (1.0.10)
ColPack 用于图着色，是 ADOL-C 稀疏矩阵支持的依赖。

```bash
tar -xzf ColPack-1.0.10.tar.gz
cd ColPack-1.0.10
autoreconf -fi
./configure --prefix=/usr/local
make -j$(nproc)
sudo make install
sudo ldconfig
```

### 2.2 编译 ADOL-C (2.7.2)
ADOL-C 是自动微分库，需要链接 ColPack。

```bash
tar -xzf ADOL-C-2.7.2.tar.gz
cd ADOL-C-releases-2.7.2
autoreconf -fi
./configure --prefix=/usr/local --with-colpack=/usr/local --enable-sparse
make -j$(nproc)
sudo make install
sudo ldconfig
```

### 2.3 编译 MUMPS (线性求解器)
使用独立脚本或 ThirdParty 源码包进行编译。

```bash
# 解压缩 ThirdParty-Mumps
mkdir -p build_mumps && unzip ThirdParty-Mumps.zip -d build_mumps
cd build_mumps/*/
./get.Mumps  # 下载核心 MUMPS 源码
./configure --prefix=/usr/local
make -j$(nproc)
sudo make install
sudo ldconfig
```
*生成的库文件名为 `libcoinmumps.so`。*

### 2.4 编译 Ipopt (3.14.x) 并开启 sIpopt
Ipopt 核心求解器，链接上述所有组件，并启用灵敏度分析插件。

```bash
tar -xzf Ipopt-3.14.16.tar.gz
cd Ipopt-releases-3.14.16
mkdir build && cd build
../configure --prefix=/usr/local \
             --with-mumps-lflags="-lcoinmumps" \
             --with-adolc-incdir=/usr/local/include/adolc \
             --with-adolc-lib="-ladolc" \
             --enable-sipopt
make -j$(nproc)
sudo make install
sudo ldconfig
```

## 3. 验证安装

检查 `/usr/local/lib` 下是否存在以下关键库文件：
- `libipopt.so`
- `libsipopt.so` (sIpopt 插件)
- `libadolc.so`
- `libColPack.so`
- `libcoinmumps.so`

## 4. CMakeLists.txt 配置建议

在项目中引用这些库时，请确保包含路径和库路径正确：
- 头文件路径：`/usr/local/include` (包含子目录 `coin-or`, `adolc`, `ColPack`)
- 库文件路径：`/usr/local/lib`
