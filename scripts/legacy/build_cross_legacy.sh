#!/bin/bash
set -e

# Get the absolute path of the current directory
WORKSPACE_DIR=$(pwd)
OUTPUT_DIR="$WORKSPACE_DIR/out/linux-arm64-legacy"
THIRD_PARTY_SRC="$WORKSPACE_DIR/thirdPartySrc"

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo ">>> Starting Legacy Cross-Compilation (Ubuntu 18.04)..."
echo "Workspace: $WORKSPACE_DIR"
echo "Output: $OUTPUT_DIR"

# Run Docker container
# We use ubuntu:18.04 (Bionic) to ensure compatibility with older GLIBC (2.27)
# which is required for the target machine (GCC 7.3.0).
docker run --rm \
    -v "$WORKSPACE_DIR:/source" \
    -v "$OUTPUT_DIR:/output" \
    -v "$THIRD_PARTY_SRC:/build/thirdPartySrc" \
    ubuntu:18.04 \
    /bin/bash /source/compile_cross_legacy.sh

echo ">>> Legacy Build Complete! Check $OUTPUT_DIR for artifacts."
