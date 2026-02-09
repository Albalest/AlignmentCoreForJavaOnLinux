# 构建脚本说明（单入口）

从 2026-01-09 起，本仓库**根目录只保留一个入口脚本**：

- `build_arm64_1804.sh`

目标：你改完 C++/JNI 代码后，直接运行这个脚本即可“一步生成”Ubuntu 18.04 可用的 Linux ARM64 动态库（并默认部署到 `API/Linux_Arm_1804`）。

## 1) 日常使用（推荐 / 默认）

默认只重新编译**项目本体**，复用缓存的 Ipopt/依赖，不会每次都重编三方：

```bash
./build_arm64_1804.sh
```

输出：
- 构建产物：`out/linux-arm64-legacy/*.so*`
- 默认部署：`/mnt/d/ARDevelop/ALCoreForLinux/API/Linux_Arm_1804/*.so*`

> 如果你第一次运行，缓存里还没有 `libipopt.so`，脚本会提示你执行一次“编译三方”。

## 2) 需要重编 Ipopt/依赖时（可选）

当你需要更新/修复 Ipopt 相关依赖或缓存丢失时：

```bash
./build_arm64_1804.sh --rebuild-thirdparty
```

如果你想强制 clean rebuild（最慢但最稳）：

```bash
./build_arm64_1804.sh --force-rebuild-thirdparty
```

## 3) 常用参数

- `--project-root <path>`：CMake 工程根目录（默认先用 `/mnt/d/ARDevelop/ALCoreForLinux`，无效会回退到 `/home/albalest/.vs/AlignmentCore`）
- `--deploy-dir <path>`：部署目录（默认 `/mnt/d/ARDevelop/ALCoreForLinux/API/Linux_Arm_1804`）
- `--no-deploy`：只生成 `out/linux-arm64-legacy`，不部署

查看完整帮助：

```bash
./build_arm64_1804.sh --help
```

## 4) 缓存说明（为什么默认不会重编 Ipopt）

脚本会把交叉编译安装前缀缓存到：

- `.cache/install_prefix_aarch64/`（宿主机目录）

并在容器里挂载到：

- `/usr/local/aarch64-linux-gnu/`

因此，默认模式下只要缓存存在，就能直接编译项目本体。

## 5) 旧脚本位置

原来根目录下的辅助脚本已移动到：

- `scripts/legacy/`

原则：**不再作为入口使用**，仅保留以便回溯/应急。
