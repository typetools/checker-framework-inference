#!/bin/bash
ROOT=$TRAVIS_BUILD_DIR/..

## Build Checker Framework
(cd $ROOT && git clone https://github.com/typetools/checker-framework.git)
# This also builds annotation-tools and jsr308-langtools
(cd checker-framework/ && ./.travis-build-without-test.sh)

gradle dist
gradle copytest
ant -f tests.xml run-tests
