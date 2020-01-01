#!/bin/bash

echo Entering "$(cd "$(dirname "$0")" && pwd -P)/$(basename "$0")" in `pwd`

# Fail the whole script if any command fails
set -e

export SHELLOPTS

. ./.travis-build-without-test.sh

./gradlew testCheckerInferenceScript
./gradlew testCheckerInferenceDevScript

./gradlew test

echo Exiting "$(cd "$(dirname "$0")" && pwd -P)/$(basename "$0")" in `pwd`
