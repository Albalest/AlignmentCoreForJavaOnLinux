#!/bin/bash
set -euo pipefail

# 单入口脚本：一键生成 Ubuntu 18.04 兼容的 Linux ARM64 动态库
# 默认：只重新编译项目本体（复用缓存的 Ipopt/依赖）
# 可选：--rebuild-thirdparty（重编译 Ipopt 及其依赖）
# 可选：--force-rebuild-thirdparty（强制 clean rebuild 三方）

IMAGE="${IMAGE:-alignment-legacy-env:latest}"

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$WORKSPACE_DIR/out/linux-arm64-legacy"
THIRD_PARTY_SRC="$WORKSPACE_DIR/thirdPartySrc"
SCRIPTS_DIR="$WORKSPACE_DIR/scripts"

DEFAULT_PROJECT_ROOT_WIN="/mnt/e/ARDevelop/ALCoreForLinux"
DEFAULT_PROJECT_ROOT_LINUX="/home/albalest/.vs/AlignmentCore"
PROJECT_ROOT="${PROJECT_ROOT:-$DEFAULT_PROJECT_ROOT_WIN}"

DEFAULT_DEPLOY_DIR="${DEFAULT_PROJECT_ROOT_WIN}/API/Linux_Arm_1804"
DEPLOY_DIR="${DEPLOY_DIR:-$DEFAULT_DEPLOY_DIR}"
DO_DEPLOY=1

MODE="project"  # project | thirdparty | thirdparty-force

CACHE_DIR="$WORKSPACE_DIR/.cache"
# 用于缓存交叉编译安装前缀（Ipopt/ColPack/ADOL-C/CoinHSL/MUMPS 等）
PREFIX_CACHE="$CACHE_DIR/install_prefix_aarch64"

# thirdparty 模式下：将 Ipopt(lib/include)保存到项目目录
SAVE_IPOPT_DIR=""

usage() {
  cat <<'USAGE'
用法：
  ./build_arm64_1804.sh [选项]

默认行为：只重新编译项目本体（复用已缓存的 Ipopt/依赖库）。

选项：
  --project-root <path>        CMake 工程根目录（默认优先 /mnt/d/ARDevelop/ALCoreForLinux，失败回退 ~/.vs/AlignmentCore）
  --no-deploy                  只生成 out/linux-arm64-legacy，不复制到 API/Linux_Arm_1804
  --deploy-dir <path>          部署目录（默认 /mnt/d/ARDevelop/ALCoreForLinux/API/Linux_Arm_1804）
  --rebuild-thirdparty         重新编译 Ipopt 及其依赖（会更慢）
  --force-rebuild-thirdparty   强制 clean rebuild 三方（最慢，最稳）
  --save-ipopt-dir <path>      将本次重编译得到的 Ipopt(lib/include)保存到该目录（默认：prebuilt/ipopt/linux-arm64-ubuntu1804）
  --image <name:tag>           使用指定 Docker 镜像（也可用环境变量 IMAGE）
  -h, --help                   显示帮助

环境变量：
  IMAGE / PROJECT_ROOT / DEPLOY_DIR
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="$2"; shift 2;;
    --deploy-dir)
      DEPLOY_DIR="$2"; shift 2;;
    --no-deploy)
      DO_DEPLOY=0; shift;;
    --rebuild-thirdparty)
      MODE="thirdparty"; shift;;
    --force-rebuild-thirdparty)
      MODE="thirdparty-force"; shift;;
    --save-ipopt-dir)
      SAVE_IPOPT_DIR="$2"; shift 2;;
    --image)
      IMAGE="$2"; shift 2;;
    -h|--help)
      usage; exit 0;;
    *)
      echo "ERROR: unknown argument: $1"; usage; exit 2;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not found in PATH"
  exit 1
fi

if [ ! -d "$THIRD_PARTY_SRC" ]; then
  echo "ERROR: thirdPartySrc not found: $THIRD_PARTY_SRC"
  exit 1
fi

mkdir -p "$OUT_DIR" "$PREFIX_CACHE"

if [ ! -f "$SCRIPTS_DIR/build_project_only_legacy.sh" ]; then
  echo "ERROR: helper script missing: $SCRIPTS_DIR/build_project_only_legacy.sh"
  exit 1
fi
if [ ! -f "$SCRIPTS_DIR/legacy/compile_cross_legacy_noipopt_patchelf.sh" ] || [ ! -f "$SCRIPTS_DIR/legacy/compile_cross_legacy_force_rebuild_noipopt_patchelf.sh" ]; then
  echo "ERROR: legacy wrapper scripts missing under: $SCRIPTS_DIR/legacy"
  echo "       expected: compile_cross_legacy_noipopt_patchelf.sh and compile_cross_legacy_force_rebuild_noipopt_patchelf.sh"
  exit 1
fi

# thirdparty 构建需要 MUMPS tarball
if [[ "$MODE" != "project" ]]; then
  if [ ! -f "$THIRD_PARTY_SRC/MUMPS_5.4.1.tar.gz" ]; then
    echo "ERROR: missing thirdPartySrc/MUMPS_5.4.1.tar.gz (required for third-party rebuild)"
    exit 1
  fi
fi

# PROJECT_ROOT 校验：
# - project 模式需要完整的 CMake 工程结构（含 source/）。
# - thirdparty 模式只需要一个存在的目录用于 docker -v 挂载。
if [[ "$MODE" = "project" ]]; then
  if [ ! -d "$PROJECT_ROOT" ] || [ ! -f "$PROJECT_ROOT/CMakeLists.txt" ]; then
    if [ -d "$DEFAULT_PROJECT_ROOT_LINUX" ] && [ -f "$DEFAULT_PROJECT_ROOT_LINUX/CMakeLists.txt" ]; then
      echo "WARN: PROJECT_ROOT invalid ($PROJECT_ROOT). Falling back to $DEFAULT_PROJECT_ROOT_LINUX"
      PROJECT_ROOT="$DEFAULT_PROJECT_ROOT_LINUX"
    else
      echo "ERROR: PROJECT_ROOT invalid or missing CMakeLists.txt: $PROJECT_ROOT"
      exit 1
    fi
  fi

  if [ ! -d "$PROJECT_ROOT/source" ]; then
    if [ -d "$PROJECT_ROOT/Source" ] && [ ! -e "$PROJECT_ROOT/source" ]; then
      echo "WARN: '$PROJECT_ROOT/source' missing; creating symlink to 'Source'"
      ln -s "Source" "$PROJECT_ROOT/source" || true
    fi
  fi
  if [ ! -d "$PROJECT_ROOT/source" ]; then
    echo "ERROR: '$PROJECT_ROOT/source' directory not found (CMake add_subdirectory(source) will fail)."
    echo "       Try: --project-root $DEFAULT_PROJECT_ROOT_LINUX"
    exit 1
  fi
else
  if [ ! -d "$PROJECT_ROOT" ]; then
    echo "WARN: PROJECT_ROOT invalid ($PROJECT_ROOT). Using WORKSPACE_DIR for mount."
    PROJECT_ROOT="$WORKSPACE_DIR"
  fi
fi

echo "=== Build mode: $MODE"
echo "    IMAGE:        $IMAGE"
echo "    PROJECT_ROOT: $PROJECT_ROOT"
echo "    OUT_DIR:      $OUT_DIR"
echo "    PREFIX_CACHE: $PREFIX_CACHE"
if [ "$DO_DEPLOY" = "1" ]; then
  echo "    DEPLOY_DIR:   $DEPLOY_DIR"
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

DOCKER_MOUNTS=(
  -v "$PROJECT_ROOT":/source
  -v "$THIRD_PARTY_SRC":/build/thirdPartySrc
  -v "$SCRIPTS_DIR":/scripts
  -v "$OUT_DIR":/output
  -v "$PREFIX_CACHE":/usr/local/aarch64-linux-gnu
)

case "$MODE" in
  project)
    docker run --rm "${DOCKER_MOUNTS[@]}" "$IMAGE" /bin/bash /scripts/build_project_only_legacy.sh
    ;;
  thirdparty)
    docker run --rm "${DOCKER_MOUNTS[@]}" "$IMAGE" /bin/bash /scripts/legacy/compile_cross_legacy_noipopt_patchelf.sh
    ;;
  thirdparty-force)
    docker run --rm "${DOCKER_MOUNTS[@]}" "$IMAGE" /bin/bash /scripts/legacy/compile_cross_legacy_force_rebuild_noipopt_patchelf.sh
    ;;
  *)
    echo "ERROR: invalid MODE=$MODE"; exit 2;;
 esac

if [[ "$MODE" != "project" ]]; then
  if [ -z "$SAVE_IPOPT_DIR" ]; then
    SAVE_IPOPT_DIR="$WORKSPACE_DIR/prebuilt/ipopt/linux-arm64-ubuntu1804"
  fi

  mkdir -p "$SAVE_IPOPT_DIR/lib" "$SAVE_IPOPT_DIR/include"
  if [ -d "$PREFIX_CACHE/lib" ]; then
    cp -a "$PREFIX_CACHE"/lib/libipopt.so* "$SAVE_IPOPT_DIR/lib/" 2>/dev/null || true
    cp -a "$PREFIX_CACHE"/lib/libsipopt.so* "$SAVE_IPOPT_DIR/lib/" 2>/dev/null || true
  fi
  if [ -d "$PREFIX_CACHE/include" ]; then
    cp -a "$PREFIX_CACHE"/include/* "$SAVE_IPOPT_DIR/include/" 2>/dev/null || true
  fi
  cp -a "$OUT_DIR"/libipopt.so* "$SAVE_IPOPT_DIR/lib/" 2>/dev/null || true
  cp -a "$OUT_DIR"/libsipopt.so* "$SAVE_IPOPT_DIR/lib/" 2>/dev/null || true
  echo "DONE: saved Ipopt to $SAVE_IPOPT_DIR"
fi

if [ "$DO_DEPLOY" = "1" ]; then
  mkdir -p "$DEPLOY_DIR"
  rm -f "$DEPLOY_DIR"/*.so* 2>/dev/null || true
  cp -a "$OUT_DIR"/*.so* "$DEPLOY_DIR"/
  echo "DONE: deployed to $DEPLOY_DIR"
else
  echo "DONE: artifacts in $OUT_DIR"
fi
