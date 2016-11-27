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


# Split $TRAVIS_REPO_SLUG into the owner and repository parts
SLUGOWNER=${TRAVIS_REPO_SLUG%/*}
SLUGREPO=${TRAVIS_REPO_SLUG##*/}

## Build Checker Framework
if [ -d $ROOT/checker-framework ] ; then
    # Older versions of git don't support the -C command-line option
    (cd $ROOT/checker-framework && git pull)
else
    (cd $ROOT && git clone --depth 1 https://github.com/${SLUGOWNER}/checker-framework.git)
fi

# This also builds annotation-tools and jsr308-langtools
(cd $ROOT/checker-framework/ && ./.travis-build-without-test.sh)

## Build plume-lib
if [ -d $ROOT/plume-lib ] ; then
    # Older versions of git don't support the -C command-line option
    (cd $ROOT/plume-lib && git pull)
else
    (cd $ROOT && git clone --quiet --depth 1 https://github.com/${SLUGOWNER}/plume-lib.git)
fi

(cd $ROOT/plume-lib/ && make)

gradle dist
