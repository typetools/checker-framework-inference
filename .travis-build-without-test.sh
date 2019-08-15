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
SLUGOWNER=`/tmp/plume-scripts/git-organization eisop`

## Build Checker Framework
if [ -d $CHECKERFRAMEWORK ] ; then
    # Fails if not currently on a branch
    git -C $CHECKERFRAMEWORK pull || true
else
    [ -d /tmp/plume-scripts ] || (cd /tmp && git clone --depth 1 https://github.com/plume-lib/plume-scripts.git)
    REPO=`/tmp/plume-scripts/git-find-fork ${SLUGOWNER} typetools checker-framework`
    BRANCH=`/tmp/plume-scripts/git-find-branch ${REPO} ${TRAVIS_PULL_REQUEST_BRANCH:-$TRAVIS_BRANCH}`
    echo "About to execute: (cd .. && git clone -b $BRANCH --single-branch --depth 1 $REPO)"
    (cd $CHECKERFRAMEWORK/.. && git clone -b ${BRANCH} --single-branch --depth 1 ${REPO}) || (cd .. && git clone -b ${BRANCH} --single-branch --depth 1 ${REPO})
fi

# This also builds annotation-tools and jsr308-langtools
(cd $CHECKERFRAMEWORK && ./.travis-build-without-test.sh downloadjdk jdk8)

# Finally build checker-framework-inference
./gradlew dist

echo "Exiting checker-framework-inference/.travis-build-without-test.sh in" `pwd`
