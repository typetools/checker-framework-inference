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

## Build Checker Framework
if [ -d $ROOT/checker-framework ] ; then
    # Older versions of git don't support the -C command-line option
    (cd $ROOT/checker-framework && git pull)
else
    (cd $ROOT && git clone https://github.com/pascaliUWat/checker-framework.git)
fi

# This also builds annotation-tools and jsr308-langtools
(cd $ROOT/checker-framework/ && ./.travis-build-without-test.sh)

gradle dist
