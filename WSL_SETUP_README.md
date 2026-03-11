# WSL2 环境配置与项目构建指南

本文档旨在帮助开发者在重装系统或迁移环境后，快速恢复 `AlignmentTest` 项目在 WSL2 (Ubuntu) 环境下的开发与构建能力。

## 1. 快速构建 (日常开发)

本项目已集成自动化构建脚本，无需手动输入 CMake 命令。

### 方法 A: 使用 VS Code 任务 (推荐)
1. 在 VS Code 中按下 `Ctrl + Shift + P`。
2. 输入 `Tasks: Run Task` 并回车。
3. 选择 **Build Native Library (C++)**。
   - 此任务会自动编译 C++ 核心库 (`libCOptimizer.so`) 并将其拷贝到 Java 项目的 `lib/` 目录。
4. 编译成功后，可以直接运行 Java 主程序。

### 方法 B: 使用命令行脚本
在项目根目录下运行：
```bash
./build_native_lib.sh
```

---

## 2. 环境重置与依赖安装 (重装系统后)

如果重新安装了 WSL2 或 Ubuntu 系统，请按照以下步骤配置环境。

### 2.1 安装基础工具
```bash
sudo apt update
sudo apt install -y build-essential g++ gfortran git cmake ninja-build \
    autoconf automake libtool pkg-config \
    liblapack-dev libblas-dev libeigen3-dev default-jdk
```

### 2.2 编译安装第三方库 (Ipopt, ADOL-C, ColPack)
本项目依赖特定的优化库，需手动编译安装。详细步骤请参考 C++ 源码目录下的指南：
`~/.vs/AlignmentCore/Linux_Environment_Setup_Guide.txt`

**简要步骤回顾：**
1. **ColPack**: 下载源码 -> `./configure --prefix=/usr/local` -> `make` -> `sudo make install`
2. **ADOL-C**: 下载源码 -> `./configure --prefix=/usr/local --with-colpack=/usr/local --enable-sparse` -> `make` -> `sudo make install`
3. **Ipopt**: 下载源码 -> `ThirdParty/Mumps/get.Mumps` -> `./configure --prefix=/usr/local` -> `make` -> `sudo make install`

### 2.3 验证库文件
安装完成后，确保 `/usr/local/lib` 下存在以下文件：
- `libipopt.so`
- `libadolc.so`
- `libColPack.so`

运行 `sudo ldconfig` 刷新动态库缓存。

## 3. 项目配置说明

### C++ 构建配置 (`CMakeLists.txt`)
位于 `~/.vs/AlignmentCore/CMakeLists.txt`。
关键配置项：
- `IPOPT_INC`: 指向 `/usr/local/include` 等头文件路径。
- `IPOPT_LIB`: 指向 `/usr/local/lib`。
- `IPOPT_LIBRARIES`: 显式链接了 `libadolc.so`, `libColPack.so`, `libipopt.so` 等。

### Java 运行配置 (`.vscode/launch.json`)
已配置 `java.library.path` 指向项目下的 `lib/` 目录，无需额外设置环境变量。

```json
"vmArgs": "-Djava.library.path=${workspaceFolder}/lib"
```

## 4. 常见问题

**Q: 运行 Java 时提示 `UnsatisfiedLinkError` 或 `symbol lookup error`?**
A: 
1. 确保已运行 "Build Native Library" 任务更新 `lib/libCOptimizer.so`。
2. 检查 `ldd lib/libCOptimizer.so`，确保所有依赖都能找到（指向 `/usr/local/lib/...` 或项目内的库）。
3. 如果提示缺少 `libadolc.so` 等符号，说明 C++ 编译时链接不完整，需检查 `CMakeLists.txt` 中的 `target_link_libraries`。

**Q: VS Code 找不到 C++ 源码目录?**
A: 默认配置假设 C++ 源码位于 `~/.vs/AlignmentCore`。如果位置改变，请修改 `build_native_lib.sh` 中的 `CPP_SOURCE_ROOT` 变量。
static {
        String libPath = System.getProperty("user.dir") + "/lib/";
        System.load(libPath + "libAlignmentCoreJava.so");
    }