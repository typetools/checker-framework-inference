#!/bin/bash
#ROOT="$( cd "$(dirname "$0")"/.. ; pwd -P )"

echo "Entering checker-framework-inference/.travis-build.sh in" `pwd`

# Fail the whole script if any command fails
set -e

export SHELLOPTS

echo "AFU: " +  $AFU

./.travis-build-without-test.sh

./gradlew testCheckerInferenceScript
./gradlew testCheckerInferenceDevScript

./gradlew test

echo "Exiting checker-framework-inference/.travis-build.sh in" `pwd`
