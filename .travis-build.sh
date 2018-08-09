#!/bin/bash
ROOT=$TRAVIS_BUILD_DIR/..

echo "Entering checker-framework-inference/.travis-build.sh"

# Fail the whole script if any command fails
set -e

export SHELLOPTS

. ./.travis-build-without-test.sh

./gradlew testCheckerInferenceScript
./gradlew testCheckerInferenceDevScript

./gradlew test

echo "Exiting checker-framework-inference/.travis-build.sh"
