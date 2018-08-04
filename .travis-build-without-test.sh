#!/bin/bash

# Fail the whole script if any command fails
set -e

export SHELLOPTS

export JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(dirname $(readlink -f $(/usr/bin/which java)))))}

export JSR308=..
export AFU=../annotation-tools/annotation-file-utilities
export CHECKERFRAMEWORK=../checker-framework

export PATH=$AFU/scripts:$JAVA_HOME/bin:$PATH

SLUGOWNER=${TRAVIS_REPO_SLUG%/*}
if [[ "$SLUGOWNER" == "" ]]; then
  SLUGOWNER=typetools
fi

## Build Checker Framework
if [ -d ../checker-framework ] ; then
  git -C ../checker-framework pull
else
  (cd .. && git clone --depth 1 https://github.com/plume-lib/plume-scripts.git)
  REPO=`../plume-scripts/git-find-repo ${SLUGOWNER} typetools checker-framework`
  echo "REPO=$REPO"
  echo "TRAVIS_PULL_REQUEST_BRANCH=$TRAVIS_PULL_REQUEST_BRANCH"
  echo "TRAVIS_BRANCH=$TRAVIS_BRANCH"
  BRANCH=`../plume-scripts/git-find-branch $REPO ${TRAVIS_PULL_REQUEST_BRANCH:-$TRAVIS_BRANCH}`
  echo "BRANCH=$BRANCH"
  (cd .. && git clone -b $BRANCH --single-branch --depth 1 $REPO) || (cd .. && git clone -b $BRANCH --single-branch --depth 1 $REPO)
fi

# This also builds annotation-tools and jsr308-langtools
(cd ../checker-framework/ && ./.travis-build-without-test.sh downloadjdk jdk8)

# Finally build checker-framework-inference
./gradlew dist
