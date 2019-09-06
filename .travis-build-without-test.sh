#!/bin/bash

echo "Entering checker-framework-inference/.travis-build-without-test.sh in" `pwd`

# Fail the whole script if any command fails
set -e

export SHELLOPTS

export JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(dirname $(readlink -f $(/usr/bin/which java)))))}

export JSR308=`readlink -f ${JSR308:-..}`
export AFU=`readlink -f ${AFU:-../annotation-tools/annotation-file-utilities}`
export CHECKERFRAMEWORK=`readlink -f ${CHECKERFRAMEWORK:-../checker-framework}`

export PATH=$AFU/scripts:$JAVA_HOME/bin:$PATH

git -C /tmp/plume-scripts pull > /dev/null 2>&1 \
  || git -C /tmp clone --depth 1 -q https://github.com/plume-lib/plume-scripts.git
eval `/tmp/plume-scripts/ci-info typetools`

## Build Checker Framework
if [ -d $CHECKERFRAMEWORK ] ; then
    # Fails if not currently on a branch
    git -C $CHECKERFRAMEWORK pull || true
else
    REPO=`/tmp/plume-scripts/git-find-fork ${CI_ORGANIZATION} typetools checker-framework`
    BRANCH=`/tmp/plume-scripts/git-find-branch ${REPO} ${CI_BRANCH}`
    echo "About to execute: (cd $CHECKERFRAMEWORK/.. && git clone -b ${BRANCH} --single-branch --depth 1 ${REPO}) || (cd .. && git clone -b ${BRANCH} --single-branch --depth 1 ${REPO})"
    (cd $CHECKERFRAMEWORK/.. && git clone -b ${BRANCH} --single-branch --depth 1 ${REPO}) || (cd .. && git clone -b ${BRANCH} --single-branch --depth 1 ${REPO})
fi

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
