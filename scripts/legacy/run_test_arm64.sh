#!/bin/bash
# Test ARM64 build in Docker

WORK_DIR="/home/albalest/AlignmentTest"

echo ">>> Starting ARM64 Test Container..."
docker run --rm --platform linux/arm64 \
    -v $(pwd):$WORK_DIR \
    -v $(pwd)/lib_arm64:$WORK_DIR/lib \
    -w $WORK_DIR \
    ubuntu:22.04 \
    /bin/bash -c "
        echo '>>> Installing Runtime Dependencies (JRE, Fortran, Lapack, X11)...'
        apt-get update >/dev/null 2>&1
        apt-get install -y openjdk-17-jre libgfortran5 libgomp1 liblapack3 libblas3 libxext6 libxrender1 libxtst6 libxi6 libx11-6 >/dev/null 2>&1
        
        echo '>>> Verifying Libraries...'
        ls -F lib/
        
        export LD_LIBRARY_PATH=$WORK_DIR/lib:$LD_LIBRARY_PATH
        export DISPLAY=host.docker.internal:0
        
        echo '>>> Running Main...'
        java -cp bin:lib Main
    "
