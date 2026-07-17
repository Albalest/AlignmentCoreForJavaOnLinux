#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk-amd64}"
BUILD_DIR="/source/out/build/linux-release-1804-x86_64"

need_cmd() {
  command -v "$1" >/dev/null 2>&1
}

copy_non_system_deps() {
  local staging_dir="$1"
  local -A seen_real=()
  local queue=()
  local re_need='^[[:space:]]*([^[:space:]]+)[[:space:]]+=>[[:space:]]+(/[^[:space:]]+)'
  local re_abs='^[[:space:]]*(/[^[:space:]]+)'

  shopt -s nullglob
  local project_libs=(/source/lib/*.so /source/lib/*.so.*)
  shopt -u nullglob

  for f in "${project_libs[@]}"; do
    [[ -e "$f" ]] || continue
    cp -a "$f" "$staging_dir/"
    real="$(readlink -f "$f" 2>/dev/null || true)"
    [[ -n "$real" && -f "$real" ]] && queue+=("$real")
  done

  add_dep() {
    local dep_path="$1"
    local dep_name="$2"
    local dep_real

    [[ -f "$dep_path" ]] || return 0

    if [[ "$dep_name" =~ ^(linux-vdso\.so.*|ld-linux.*\.so.*|libc\.so\.6|libm\.so\.6|libpthread\.so\.0|librt\.so\.1|libdl\.so\.2|libutil\.so\.1|libresolv\.so\.2)$ ]]; then
      return 0
    fi

    if [[ ! -e "$staging_dir/$dep_name" ]]; then
      cp -L "$dep_path" "$staging_dir/$dep_name"
    fi

    dep_real="$(readlink -f "$dep_path" 2>/dev/null || true)"
    [[ -n "$dep_real" && -f "$dep_real" ]] && queue+=("$dep_real")
  }

  while [[ ${#queue[@]} -gt 0 ]]; do
    current="${queue[0]}"
    queue=("${queue[@]:1}")

    [[ -f "$current" ]] || continue
    if [[ -n "${seen_real[$current]:-}" ]]; then
      continue
    fi
    seen_real["$current"]=1

    while IFS= read -r line; do
      [[ "$line" == *"not found"* ]] && continue

      if [[ "$line" =~ $re_need ]]; then
        add_dep "${BASH_REMATCH[2]}" "${BASH_REMATCH[1]}"
        continue
      fi

      if [[ "$line" =~ $re_abs ]]; then
        add_dep "${BASH_REMATCH[1]}" "$(basename "${BASH_REMATCH[1]}")"
      fi
    done < <(ldd "$current" 2>/dev/null || true)
  done
}

if ! need_cmd cmake || ! need_cmd ninja || ! need_cmd ldd; then
  echo "ERROR: cmake, ninja and ldd must exist inside the container"
  exit 2
fi

if [[ ! -f /source/CMakeLists.txt ]]; then
  echo "ERROR: /source does not look like an AlignmentCore root"
  exit 1
fi

if ! ldconfig -p | grep -q 'libipopt.so'; then
  echo "ERROR: libipopt.so not found in container runtime paths"
  exit 2
fi

if ! ldconfig -p | grep -q 'libadolc.so'; then
  echo "ERROR: libadolc.so not found in container runtime paths"
  exit 2
fi

echo ">>> Configuring x86_64 release build in Ubuntu 18.04"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

cmake -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DJAVA_HOME="$JAVA_HOME" \
  -DJAVA_AWT_LIBRARY=NotNeeded \
  -DJAVA_AWT_INCLUDE_PATH=NotNeeded \
  -DJAVA_JVM_LIBRARY=NotNeeded \
  -DJAVA_INCLUDE_PATH="$JAVA_HOME/include" \
  -DJAVA_INCLUDE_PATH2="$JAVA_HOME/include/linux" \
  -DCMAKE_INSTALL_RPATH='\$ORIGIN' \
  -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON \
  /source

echo ">>> Building all shared libraries"
ninja -j"$(nproc)" \
  COptimizer \
  AlignmentOptimizer \
  BallastlessRailOpt \
  CMatrixByEigen \
  SurveyingCore \
  AlignmentCore \
  TQICal \
  AlignmentFittingCore \
  AlignmentCoreJava \
  MicroNetWork

echo ">>> Collecting project libraries and non-system runtime dependencies"
rm -rf /output/*
mkdir -p /output
copy_non_system_deps /output

if need_cmd patchelf; then
  shopt -s nullglob
  for f in /output/*.so /output/*.so.*; do
    [[ -f "$f" ]] || continue
    patchelf --set-rpath '\$ORIGIN' "$f" 2>/dev/null || true
  done
  shopt -u nullglob
fi

echo ">>> Done. Output is ready under /output"