#!/usr/bin/env bash
set -euo pipefail

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${DEPLOY_DIR:-/mnt/e/ARDevelop/ALCoreForLinux/API/Java}"
STAGING_DIR="${STAGING_DIR:-$WORKSPACE_DIR/out/java-api-deploy}"
PACKAGE_NAME="${PACKAGE_NAME:-ALCore_Java_API_$(date +%Y%m%d).tar.gz}"
DO_PACKAGE=1

SRC_DIRS=(
  "AlBallastlessRailOpter"
  "AlOpter"
  "AlignmentCore"
)

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/export_java_api.sh [options]

Purpose:
  Export Java source files from AlBallastlessRailOpter, AlOpter and AlignmentCore
  to a target API/Java directory, with optional tar.gz packaging.

Options:
  --deploy-dir <path>    Output deploy directory (default: /mnt/e/ARDevelop/ALCoreForLinux/API/Java)
  --staging-dir <path>   Temporary staging directory (default: <repo>/out/java-api-deploy)
  --package-name <name>  Package filename (default: ALCore_Java_API_YYYYMMDD.tar.gz)
  --no-package           Copy files only, do not create tar.gz
  -h, --help             Show help

Environment variables:
  DEPLOY_DIR, STAGING_DIR, PACKAGE_NAME
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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

cd "$WORKSPACE_DIR"

for d in "${SRC_DIRS[@]}"; do
  if [[ ! -d "$d" ]]; then
    echo "ERROR: source directory not found: $WORKSPACE_DIR/$d"
    exit 1
  fi
done

rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR" "$DEPLOY_DIR"

for d in "${SRC_DIRS[@]}"; do
  mkdir -p "$STAGING_DIR/$d"
  find "$d" -type f -name '*.java' -print0 | while IFS= read -r -d '' f; do
    rel="${f#${d}/}"
    mkdir -p "$STAGING_DIR/$d/$(dirname "$rel")"
    cp -a "$f" "$STAGING_DIR/$d/$rel"
  done
done

# Refresh destination Java sources.
for d in "${SRC_DIRS[@]}"; do
  rm -rf "$DEPLOY_DIR/$d"
  cp -a "$STAGING_DIR/$d" "$DEPLOY_DIR/$d"
done

if [[ "$DO_PACKAGE" == "1" ]]; then
  rm -f "$DEPLOY_DIR/$PACKAGE_NAME"
  (
    cd "$DEPLOY_DIR"
    tar -czf "$PACKAGE_NAME" AlBallastlessRailOpter AlOpter AlignmentCore
  )
  echo "DONE: packaged -> $DEPLOY_DIR/$PACKAGE_NAME"
fi

echo "DONE: deployed Java sources -> $DEPLOY_DIR"
for d in "${SRC_DIRS[@]}"; do
  c=$(find "$DEPLOY_DIR/$d" -type f -name '*.java' | wc -l | tr -d ' ')
  echo "$d: $c files"
done
