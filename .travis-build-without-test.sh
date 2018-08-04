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
  set +e
  git ls-remote https://github.com/${SLUGOWNER}/checker-framework.git &>-
  if [ "$?" -ne 0 ]; then
      CFSLUGOWNER=typetools
  else
      CFSLUGOWNER=${SLUGOWNER}
  fi
  REPO=https://github.com/${CFSLUGOWNER}/checker-framework.git
  echo "TRAVIS_PULL_REQUEST_BRANCH=$TRAVIS_PULL_REQUEST_BRANCH"
  echo "TRAVIS_BRANCH=$TRAVIS_BRANCH"
  BRANCH=${TRAVIS_PULL_REQUEST_BRANCH:-$TRAVIS_BRANCH}
  echo "BRANCH=$BRANCH"
  git ls-remote --heads ${REPO} ${BRANCH} | grep ${BRANCH} >/dev/null
  if [ "$?" == "1" ] ; then
    BRANCH=master
  fi
  set -e
  (cd .. && git clone -b $BRANCH --single-branch --depth 1 $REPO)
fi



if (git -C ../checker-framework show-branch remotes/origin/$BRANCH > /dev/null 2>&1) ; then
  echo "Running:  git -C ../checker-framework checkout $BRANCH"
  git -C ../checker-framework checkout $BRANCH
  echo "... done: git -C ../checker-framework checkout $BRANCH"
else
  echo "Branch $BRANCH does not exist"
  git -C ../checker-framework branch -a
fi

# This also builds annotation-tools and jsr308-langtools
(cd ../checker-framework/ && ./.travis-build-without-test.sh downloadjdk jdk8)

# Finally build checker-framework-inference
./gradlew dist
