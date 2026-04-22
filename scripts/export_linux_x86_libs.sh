#!/usr/bin/env bash
set -euo pipefail

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_DIR="${LIB_DIR:-$WORKSPACE_DIR/lib}"
DEPLOY_DIR="${DEPLOY_DIR:-/mnt/e/ARDevelop/ALCoreForLinux/API/Linux-x86}"
STAGING_DIR="${STAGING_DIR:-$WORKSPACE_DIR/out/linux-x86-deploy}"
PACKAGE_NAME="${PACKAGE_NAME:-ALCore_Linux_x86_Deployment_$(date +%Y%m%d).tar.gz}"
DO_PACKAGE=1

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/export_linux_x86_libs.sh [options]

Purpose:
  Export all .so/.so.* under current lib directory, resolve runtime dependencies
  with ldd, and deploy/package to a target directory.

Options:
  --lib-dir <path>       Source lib directory (default: <repo>/lib)
  --deploy-dir <path>    Output deploy directory (default: /mnt/e/ARDevelop/ALCoreForLinux/API/Linux-x86)
  --staging-dir <path>   Temporary staging directory (default: <repo>/out/linux-x86-deploy)
  --package-name <name>  Package filename (default: ALCore_Linux_x86_Deployment_YYYYMMDD.tar.gz)
  --no-package           Copy .so files only, do not create tar.gz
  -h, --help             Show help

Environment variables:
  LIB_DIR, DEPLOY_DIR, STAGING_DIR, PACKAGE_NAME
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --lib-dir)
      LIB_DIR="$2"; shift 2 ;;
    --deploy-dir)
      DEPLOY_DIR="$2"; shift 2 ;;
    --staging-dir)
      STAGING_DIR="$2"; shift 2 ;;
    --package-name)
      PACKAGE_NAME="$2"; shift 2 ;;
    --no-package)
      DO_PACKAGE=0; shift ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "ERROR: unknown option: $1"
      usage
      exit 2 ;;
  esac
done

if [[ ! -d "$LIB_DIR" ]]; then
  echo "ERROR: lib directory not found: $LIB_DIR"
  exit 1
fi
if ! command -v ldd >/dev/null 2>&1; then
  echo "ERROR: ldd command not found"
  exit 1
fi

shopt -s nullglob
lib_files=("$LIB_DIR"/*.so "$LIB_DIR"/*.so.*)
shopt -u nullglob
if [[ ${#lib_files[@]} -eq 0 ]]; then
  echo "ERROR: no .so files found under: $LIB_DIR"
  exit 1
fi

rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR" "$DEPLOY_DIR"

# Keep original symlink layout from project lib directory.
cp -a "$LIB_DIR"/*.so "$LIB_DIR"/*.so.* "$STAGING_DIR"/ 2>/dev/null || true

# Resolve dependencies recursively.
declare -A seen_real
queue=()
for f in "${lib_files[@]}"; do
  real="$(readlink -f "$f" 2>/dev/null || true)"
  [[ -n "$real" && -f "$real" ]] && queue+=("$real")
done

add_dep_with_alias() {
  local dep_path="$1"
  local dep_name="$2"

  [[ -f "$dep_path" ]] || return 0

  # Copy as SONAME entry so runtime can resolve by DT_NEEDED name.
  if [[ ! -e "$STAGING_DIR/$dep_name" ]]; then
    cp -L "$dep_path" "$STAGING_DIR/$dep_name"
  fi

  local real
  real="$(readlink -f "$dep_path" 2>/dev/null || true)"
  [[ -n "$real" && -f "$real" ]] && queue+=("$real")
}

while [[ ${#queue[@]} -gt 0 ]]; do
  current="${queue[0]}"
  queue=("${queue[@]:1}")

  [[ -f "$current" ]] || continue
  if [[ -n "${seen_real[$current]:-}" ]]; then
    continue
  fi
  seen_real["$current"]=1

  re_need='^[[:space:]]*([^[:space:]]+)[[:space:]]+=>[[:space:]]+(/[^[:space:]]+)'
  re_abs='^[[:space:]]*(/[^[:space:]]+)'

  while IFS= read -r line; do
    [[ "$line" == *"not found"* ]] && continue

    if [[ "$line" =~ $re_need ]]; then
      need_name="${BASH_REMATCH[1]}"
      need_path="${BASH_REMATCH[2]}"
      add_dep_with_alias "$need_path" "$need_name"
      continue
    fi

    if [[ "$line" =~ $re_abs ]]; then
      need_path="${BASH_REMATCH[1]}"
      need_name="$(basename "$need_path")"
      add_dep_with_alias "$need_path" "$need_name"
      continue
    fi
  done < <(ldd "$current" 2>/dev/null || true)
done

# Refresh deploy directory shared libs.
rm -f "$DEPLOY_DIR"/*.so "$DEPLOY_DIR"/*.so.* 2>/dev/null || true
cp -a "$STAGING_DIR"/*.so "$STAGING_DIR"/*.so.* "$DEPLOY_DIR"/ 2>/dev/null || true

if [[ "$DO_PACKAGE" == "1" ]]; then
  rm -f "$DEPLOY_DIR/$PACKAGE_NAME"
  (
    cd "$DEPLOY_DIR"
    tar -czf "$PACKAGE_NAME" ./*.so*
  )
  echo "DONE: packaged -> $DEPLOY_DIR/$PACKAGE_NAME"
fi

echo "DONE: deployed .so files -> $DEPLOY_DIR"
ls -1 "$DEPLOY_DIR"/*.so* 2>/dev/null | sed -n '1,200p'
