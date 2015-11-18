#!/bin/bash
ROOT=$TRAVIS_BUILD_DIR/..
cd $ROOT
git clone https://github.com/typetools/checker-framework.git
cd checker-framework/
./.travis-build.sh
# This also builds annotation-tools and jsr308-langtools

cd $ROOT/checker-framework-inference
gradle dist
gradle copytest
ant -f tests.xml run-tests
