#!/bin/bash

echo "Entering checker-framework-inference/.travis-build-without-test.sh in" `pwd`

# Fail the whole script if any command fails
set -e

export SHELLOPTS

if [ "$(uname)" == "Darwin" ] ; then
  export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home)}
else
  export JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which javac))))}
fi

export JSR308="${JSR308:-$(cd .. && pwd -P)}"
export AFU="${AFU:-$(pwd -P)/../annotation-tools/annotation-file-utilities}"
export CHECKERFRAMEWORK="${CHECKERFRAMEWORK:-$(pwd -P)/../checker-framework}"

export PATH=$AFU/scripts:$JAVA_HOME/bin:$PATH

git -C /tmp/plume-scripts pull > /dev/null 2>&1 \
  || git -C /tmp clone --depth 1 -q https://github.com/plume-lib/plume-scripts.git

## Build Checker Framework
/tmp/plume-scripts/git-clone-related opprop checker-framework ${CHECKERFRAMEWORK}

# This also builds annotation-tools
(cd $CHECKERFRAMEWORK && checker/bin-devel/build.sh downloadjdk jdk8)

# Finally build checker-framework-inference
./gradlew dist

echo "Exiting checker-framework-inference/.travis-build-without-test.sh in" `pwd`
