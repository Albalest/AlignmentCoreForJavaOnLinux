#!/usr/bin/env bash
set -euo pipefail

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_ROOT="${SOURCE_ROOT:-$HOME/.vs/AlignmentCore}"
OUTPUT_DIR="${OUTPUT_DIR:-$WORKSPACE_DIR/out/linux-x86_64-1804}"
WORKSPACE_LIB_DIR="${WORKSPACE_LIB_DIR:-$WORKSPACE_DIR/lib_x86}"
IMAGE="${IMAGE:-alignment-x86_64-1804-env:latest}"
DOCKERFILE="${DOCKERFILE:-$WORKSPACE_DIR/Dockerfile.x86_64_1804}"
BASE_IMAGE="${BASE_IMAGE:-ubuntu:18.04}"
HELPER_SCRIPT="/workspace/scripts/build_project_only_x86_64_1804.sh"
REBUILD_IMAGE=1
SYNC_TO_WORKSPACE=1

usage() {
  cat <<'USAGE'
Usage:
  ./build_x86_64_1804.sh [options]

Purpose:
  Rebuild AlignmentCore x86_64 shared libraries in an Ubuntu 18.04 container so
  the generated binaries stay compatible with glibc 2.28-era deployment hosts.

Options:
  --source-root <path>        AlignmentCore source root (default: ~/.vs/AlignmentCore)
  --output-dir <path>         Output directory for rebuilt libraries
  --workspace-lib-dir <path>  Target workspace lib directory to refresh
  --image <name>              Docker image tag to use
  --dockerfile <path>         Dockerfile path
  --base-image <name>         Base image for the Dockerfile build
  --no-image-build            Reuse existing Docker image
  --no-sync                   Do not copy rebuilt .so files into workspace lib
  -h, --help                  Show help

Environment variables:
  SOURCE_ROOT, OUTPUT_DIR, WORKSPACE_LIB_DIR, IMAGE, DOCKERFILE, BASE_IMAGE
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-root)
      SOURCE_ROOT="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --workspace-lib-dir)
      WORKSPACE_LIB_DIR="$2"
      shift 2
      ;;
    --image)
      IMAGE="$2"
      shift 2
      ;;
    --dockerfile)
      DOCKERFILE="$2"
      shift 2
      ;;
    --base-image)
      BASE_IMAGE="$2"
      shift 2
      ;;
    --no-image-build)
      REBUILD_IMAGE=0
      shift
      ;;
    --no-sync)
      SYNC_TO_WORKSPACE=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: unknown option: $1"
      usage
      exit 2
      ;;
  esac
done

need_cmd() {
  command -v "$1" >/dev/null 2>&1
}

check_compat() {
  local dir="$1"
  local bad=0
  local max_glibc
  local max_glibcxx

  shopt -s nullglob
  local files=("$dir"/*.so "$dir"/*.so.*)
  shopt -u nullglob

  if [[ ${#files[@]} -eq 0 ]]; then
    echo "ERROR: no shared libraries found under $dir"
    return 1
  fi

  for f in "${files[@]}"; do
    [[ -f "$f" ]] || continue

    max_glibc="$(readelf -W --version-info "$f" 2>/dev/null | grep -o 'GLIBC_[0-9]\+\.[0-9]\+' | sort -V | tail -n 1 || true)"
    max_glibcxx="$(strings -a "$f" 2>/dev/null | grep -o 'GLIBCXX_[0-9]\+\.[0-9]\+\(\.[0-9]\+\)*' | sort -V | tail -n 1 || true)"

    if [[ -n "$max_glibc" ]] && [[ "${max_glibc#GLIBC_}" != "$(printf '%s\n%s\n' "${max_glibc#GLIBC_}" "2.28" | sort -V | head -n 1)" ]]; then
      echo "BAD(GLIBC too new): $(basename "$f") -> $max_glibc"
      bad=1
    fi

    if [[ "$(basename "$f")" == libstdc++.so.* ]]; then
      continue
    fi

    if [[ -n "$max_glibcxx" ]] && [[ "${max_glibcxx#GLIBCXX_}" != "$(printf '%s\n%s\n' "${max_glibcxx#GLIBCXX_}" "3.4.24" | sort -V | head -n 1)" ]]; then
      echo "BAD(GLIBCXX too new): $(basename "$f") -> $max_glibcxx"
      bad=1
    fi
  done

  if [[ "$bad" -ne 0 ]]; then
    echo "ERROR: compatibility check failed for $dir"
    return 2
  fi

  echo "Compatibility check passed for $dir"
}

if ! need_cmd docker; then
  echo "ERROR: docker not found in PATH"
  exit 1
fi

if ! need_cmd readelf || ! need_cmd strings; then
  echo "ERROR: readelf and strings are required on the host for compatibility checks"
  exit 1
fi

if [[ ! -f "$SOURCE_ROOT/CMakeLists.txt" ]]; then
  echo "ERROR: invalid source root: $SOURCE_ROOT"
  exit 1
fi

if [[ ! -f "$DOCKERFILE" ]]; then
  echo "ERROR: dockerfile not found: $DOCKERFILE"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

if [[ "$REBUILD_IMAGE" == "1" ]]; then
  echo ">>> Building Docker image: $IMAGE"
  docker build --build-arg BASE_IMAGE="$BASE_IMAGE" -f "$DOCKERFILE" -t "$IMAGE" "$WORKSPACE_DIR"
fi

echo ">>> Rebuilding x86_64 libraries in Ubuntu 18.04 container"
docker run --rm \
  -v "$SOURCE_ROOT":/source \
  -v "$OUTPUT_DIR":/output \
  -v "$WORKSPACE_DIR":/workspace \
  "$IMAGE" /bin/bash "$HELPER_SCRIPT"

echo ">>> Validating rebuilt artifacts"
check_compat "$OUTPUT_DIR"

if [[ "$SYNC_TO_WORKSPACE" == "1" ]]; then
  mkdir -p "$WORKSPACE_LIB_DIR"
  rm -f "$WORKSPACE_LIB_DIR"/*.so "$WORKSPACE_LIB_DIR"/*.so.* 2>/dev/null || true
  cp -a "$OUTPUT_DIR"/*.so "$OUTPUT_DIR"/*.so.* "$WORKSPACE_LIB_DIR"/ 2>/dev/null || true
  check_compat "$WORKSPACE_LIB_DIR"
fi

echo "DONE: rebuilt libraries are under $OUTPUT_DIR"
if [[ "$SYNC_TO_WORKSPACE" == "1" ]]; then
  echo "DONE: workspace lib refreshed at $WORKSPACE_LIB_DIR"
fi