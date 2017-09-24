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
    # Older versions of git don't support the -C command-line option
    (cd ../checker-framework && git pull)
else
    set +e
    git ls-remote https://github.com/${SLUGOWNER}/checker-framework.git &>-
    if [ "$?" -ne 0 ]; then
	CFSLUGOWNER=typetools
    else
	CFSLUGOWNER=${SLUGOWNER}
    fi
    set -e
    (cd .. && git clone --depth 1 https://github.com/${CFSLUGOWNER}/checker-framework.git)
fi

# This also builds annotation-tools and jsr308-langtools
(cd ../checker-framework/ && ./.travis-build-without-test.sh downloadjdk jdk8)

## Build plume-lib
if [ -d ../plume-lib ] ; then
    # Older versions of git don't support the -C command-line option
    (cd ../plume-lib && git pull)
else
    set +e
    git ls-remote https://github.com/${SLUGOWNER}/plume-lib.git &>-
    if [ "$?" -ne 0 ]; then
	PLSLUGOWNER=mernst
    else
	PLSLUGOWNER=${SLUGOWNER}
    fi
    set -e
    (cd .. && git clone --depth 1 https://github.com/${PLSLUGOWNER}/plume-lib.git)
fi

(cd ../plume-lib/ && make)

# Finally build checker-framework-inference
gradle dist
