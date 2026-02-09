#!/usr/bin/env bash
set -euo pipefail

# Wrapper: run the original legacy third-party build/export script, then overwrite
# exported libipopt/libsipopt with the original copies from the install prefix.
#
# Rationale: some patchelf builds can rewrite ELF program headers on aarch64 in a way
# that breaks the Ubuntu 18.04 loader ("ELF load command address/offset not properly aligned").

SKIP_PROJECT_BUILD=1 /bin/bash /scripts/legacy/compile_cross_legacy.sh

HOST_ARCH=aarch64-linux-gnu
INSTALL_PREFIX=/usr/local/$HOST_ARCH

for dir in "$INSTALL_PREFIX/lib64" "$INSTALL_PREFIX/lib"; do
  [ -d "$dir" ] || continue
  if ls "$dir"/libipopt.so* >/dev/null 2>&1 || ls "$dir"/libsipopt.so* >/dev/null 2>&1; then
    rm -f /output/libipopt.so* /output/libsipopt.so* 2>/dev/null || true
    cp -P "$dir"/libipopt.so* /output/ 2>/dev/null || true
    cp -P "$dir"/libsipopt.so* /output/ 2>/dev/null || true
  fi
  break
done

echo ">>> Wrapper done: restored libipopt/libsipopt from $INSTALL_PREFIX"
