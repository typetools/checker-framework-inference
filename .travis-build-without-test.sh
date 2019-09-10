#!/bin/bash

echo "Entering checker-framework-inference/.travis-build-without-test.sh in" `pwd`

# Fail the whole script if any command fails
set -e

export SHELLOPTS

export JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(dirname $(readlink -f $(/usr/bin/which java)))))}

export JSR308="$(cd "${JSR308:-..}" && pwd -P)"
export AFU="$(cd "${AFU:-../annotation-tools/annotation-file-utilities}" && pwd -P)"
export CHECKERFRAMEWORK="$(cd "${CHECKERFRAMEWORK:-../checker-framework}" && pwd -P)"

export PATH=$AFU/scripts:$JAVA_HOME/bin:$PATH

git -C /tmp/plume-scripts pull > /dev/null 2>&1 \
  || git -C /tmp clone --depth 1 -q https://github.com/plume-lib/plume-scripts.git

## Build Checker Framework
/tmp/plume-scripts/git-clone-related typetools checker-framework ${CHECKERFRAMEWORK}

# This also builds annotation-tools
(cd $CHECKERFRAMEWORK && checker/bin-devel/build.sh downloadjdk jdk8)

# jsr308-langtools
if [ -d ../jsr308-langtools ] ; then
    (cd ../jsr308-langtools && hg pull && hg update)
else
    echo "Running:  (cd .. && hg clone https://bitbucket.org/eisop/jsr308-langtools)"
    (cd .. && (hg clone https://bitbucket.org/eisop/jsr308-langtools || hg clone https://bitbucket.org/eisop/jsr308-langtools))
    echo "... done: (cd .. && hg clone https://bitbucket.org/eisop/jsr308-langtools)"
fi
(cd ../jsr308-langtools/ && ./.travis-build-without-test.sh)


# Finally build checker-framework-inference
./gradlew dist

echo "Exiting checker-framework-inference/.travis-build-without-test.sh in" `pwd`
