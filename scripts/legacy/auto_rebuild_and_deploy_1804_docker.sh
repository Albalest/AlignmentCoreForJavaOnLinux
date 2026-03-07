#!/bin/bash
set -euo pipefail

# Rebuild all native aarch64 libs inside Ubuntu 18.04 Docker image (legacy)
# and deploy to Windows API/Linux_Arm_1804 directory.

IMAGE="${IMAGE:-alignment-legacy-env:latest}"
PROJECT_ROOT="${PROJECT_ROOT:-/mnt/d/ARDevelop/ALCoreForLinux}"
TARGET_API_DIR="${TARGET_API_DIR:-/mnt/d/ARDevelop/ALCoreForLinux/API/Linux_Arm_1804}"

WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$WORKSPACE_DIR/out/linux-arm64-legacy"
THIRD_PARTY_SRC="${THIRD_PARTY_SRC:-$WORKSPACE_DIR/thirdPartySrc}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not found in PATH"
  exit 1
fi

if [ ! -d "$PROJECT_ROOT" ] || [ ! -f "$PROJECT_ROOT/CMakeLists.txt" ]; then
  echo "ERROR: PROJECT_ROOT invalid or missing CMakeLists.txt: $PROJECT_ROOT"
  exit 1
fi

if [ ! -d "$THIRD_PARTY_SRC" ]; then
  echo "ERROR: thirdPartySrc not found: $THIRD_PARTY_SRC"
  exit 1
fi

# Ensure MUMPS tarball exists where compile_cross_legacy.sh expects it.
if [ ! -f "$THIRD_PARTY_SRC/MUMPS_5.5.1.tar.gz" ]; then
  echo "ERROR: missing thirdPartySrc/MUMPS_5.5.1.tar.gz"
  exit 1
fi

if [ ! -x "$WORKSPACE_DIR/compile_cross_legacy_force_rebuild.sh" ]; then
  echo "ERROR: missing or not executable: $WORKSPACE_DIR/compile_cross_legacy_force_rebuild.sh"
  exit 1
fi

echo "=== [1/3] Cleaning output dir: $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "=== [2/3] Docker rebuild (Ubuntu 18.04 legacy): $IMAGE"

docker run --rm \
  -v "$PROJECT_ROOT":/source \
  -v "$THIRD_PARTY_SRC":/build/thirdPartySrc \
  -v "$WORKSPACE_DIR":/scripts \
  -v "$OUT_DIR":/output \
  "$IMAGE" /bin/bash /scripts/compile_cross_legacy_force_rebuild.sh

if [ "${SKIP_COMPAT_CHECK:-0}" != "1" ]; then
  echo "=== [compat] Checking GLIBC/GLIBCXX symbol versions in $OUT_DIR"
  bad=0
  shopt -s nullglob
  for f in "$OUT_DIR"/*.so*; do
    [ -f "$f" ] || continue

    # GLIBCXX >= 3.4.25 is too new for GCC 7.x (expects <= 3.4.24)
    if strings -a "$f" | grep -Eq 'GLIBCXX_3\.4\.(2[5-9]|[3-9][0-9])'; then
      echo "BAD(GLIBCXX too new): $(basename "$f")"
      bad=1
    fi

    # GLIBC >= 2.29 is too new for target GLIBC 2.28
    if strings -a "$f" | grep -Eq 'GLIBC_2\.(2[9-9]|[3-9][0-9])'; then
      echo "BAD(GLIBC too new): $(basename "$f")"
      bad=1
    fi
  done
  shopt -u nullglob

  if [ "$bad" -ne 0 ]; then
    echo "ERROR: compatibility check failed. Set SKIP_COMPAT_CHECK=1 to bypass."
    exit 2
  fi
fi

echo "=== [3/3] Deploying to: $TARGET_API_DIR"
mkdir -p "$TARGET_API_DIR"
rm -f "$TARGET_API_DIR"/*.so* 2>/dev/null || true
cp -a "$OUT_DIR"/*.so* "$TARGET_API_DIR"/

echo "DONE: rebuilt and deployed to $TARGET_API_DIR"
