#!/bin/bash
ROOT=$TRAVIS_BUILD_DIR/..

# Fail the whole script if any command fails
set -e

export SHELLOPTS

export JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(dirname $(readlink -f $(/usr/bin/which java)))))}

export JSR308=$ROOT
export AFU=$ROOT/annotation-tools/annotation-file-utilities
export CHECKERFRAMEWORK=$ROOT/checker-framework

export PATH=$AFU/scripts:$JAVA_HOME/bin:$PATH

SLUGOWNER=${TRAVIS_REPO_SLUG%/*}

## Build Checker Framework
if [ -d $ROOT/checker-framework ] ; then
    # Older versions of git don't support the -C command-line option
    (cd $ROOT/checker-framework && git pull)
else
    set +e
    git ls-remote https://github.com/${SLUGOWNER}/checker-framework.git &>-
    if [ "$?" -ne 0 ]; then
	CFSLUGOWNER=typetools
    else
	CFSLUGOWNER=${SLUGOWNER}
    fi
    set -e
    (cd $ROOT && git clone --depth 1 https://github.com/${CFSLUGOWNER}/checker-framework.git)
fi

# This also builds annotation-tools and jsr308-langtools
(cd $ROOT/checker-framework/ && ./.travis-build-without-test.sh downloadjdk)

## Build plume-lib
if [ -d $ROOT/plume-lib ] ; then
    # Older versions of git don't support the -C command-line option
    (cd $ROOT/plume-lib && git pull)
else
    set +e
    git ls-remote https://github.com/${SLUGOWNER}/plume-lib.git &>-
    if [ "$?" -ne 0 ]; then
	PLSLUGOWNER=typetools
    else
	PLSLUGOWNER=${SLUGOWNER}
    fi
    set -e
    (cd $ROOT && git clone --quiet --depth 1 https://github.com/${PLSLUGOWNER}/plume-lib.git)
fi

(cd $ROOT/plume-lib/ && make)

gradle dist
