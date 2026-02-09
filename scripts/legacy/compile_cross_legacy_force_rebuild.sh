#!/bin/bash
set -euo pipefail

# Wrapper script: force clean rebuild of key third-party libs inside Ubuntu 18.04 chroot
# without modifying compile_cross_legacy.sh.

HOST_ARCH=aarch64-linux-gnu
INSTALL_PREFIX=/usr/local/$HOST_ARCH
CC=$HOST_ARCH-gcc
AR=$HOST_ARCH-ar
RANLIB=$HOST_ARCH-ranlib

# 1) Clean installed artifacts that can cause old binaries to be reused.
echo ">>> [force-rebuild] Cleaning installed third-party artifacts under: $INSTALL_PREFIX"
rm -f "$INSTALL_PREFIX/lib"/libColPack.so* 2>/dev/null || true
rm -f "$INSTALL_PREFIX/lib"/libadolc.so* 2>/dev/null || true
rm -f "$INSTALL_PREFIX/lib"/libcoinhsl.so* 2>/dev/null || true
rm -f "$INSTALL_PREFIX/lib"/libdmumps* "$INSTALL_PREFIX/lib"/libmumps_common* "$INSTALL_PREFIX/lib"/libpord* "$INSTALL_PREFIX/lib"/libmpiseq* 2>/dev/null || true
rm -rf "$INSTALL_PREFIX/include/mumps" 2>/dev/null || true

# 2) Ensure libpord is available (MUMPS PORD Makefile is flaky under cross builds).
# Ipopt links with -lpord, so we build a standalone libpord.a from the MUMPS tarball.
echo ">>> [force-rebuild] Building standalone libpord.a into $INSTALL_PREFIX/lib"
MUMPS_TAR=""
for p in \
  "/build/thirdPartySrc/Ipopt/ThirdParty/Mumps/MUMPS_5.4.1.tar.gz" \
  "/build/thirdPartySrc/MUMPS_5.4.1.tar.gz"; do
  if [ -f "$p" ]; then MUMPS_TAR="$p"; break; fi
done
if [ -z "$MUMPS_TAR" ]; then
  echo "ERROR: MUMPS_5.4.1.tar.gz not found (needed to build libpord.a)"
  exit 1
fi

TMP_PORD="/tmp/PORD_BUILD_$$"
rm -rf "$TMP_PORD"
mkdir -p "$TMP_PORD"
tar -xzf "$MUMPS_TAR" -C "$TMP_PORD" --strip-components=1

if [ ! -d "$TMP_PORD/PORD/lib" ]; then
  echo "ERROR: PORD/lib not found inside MUMPS tarball"
  exit 1
fi

pushd "$TMP_PORD/PORD/lib" >/dev/null
# Compile all C sources in PORD/lib
for c in *.c; do
  [ -f "$c" ] || continue
  o="${c%.c}.o"
  "$CC" -I../include -O2 -fPIC -c "$c" -o "$o"
done
"$AR" rv libpord.a ./*.o
"$RANLIB" libpord.a
mkdir -p "$INSTALL_PREFIX/lib"
cp -f libpord.a "$INSTALL_PREFIX/lib/"
popd >/dev/null
rm -rf "$TMP_PORD"

# 3) Clean previous exported artifacts in /output
echo ">>> [force-rebuild] Cleaning previous exported artifacts in /output"
rm -f /output/*.so* 2>/dev/null || true

# 4) Delegate to the original build script
echo ">>> [force-rebuild] Delegating to /scripts/legacy/compile_cross_legacy.sh"
exec /bin/bash /scripts/legacy/compile_cross_legacy.sh
