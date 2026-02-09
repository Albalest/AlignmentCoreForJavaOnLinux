#!/bin/bash
# Run Logic Test for AlignmentTest (Offline Mode)

BASE_DIR=$(cd "$(dirname "$0")"; pwd)

export JAVA_HOME="$BASE_DIR/jre"
export PATH="$JAVA_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$BASE_DIR/lib:$BASE_DIR/sys_lib:$LD_LIBRARY_PATH"

echo ">>> Running TestLogic..."
"$JAVA_HOME/bin/java" -cp "$BASE_DIR/bin" TestLogic
