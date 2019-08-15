#!/bin/bash
#ROOT="$( cd "$(dirname "$0")"/.. ; pwd -P )"

echo "Entering checker-framework-inference/.travis-build.sh in" `pwd`

# Fail the whole script if any command fails
set -e

export SHELLOPTS


./.travis-build-without-test.sh

echo "AFU: ${AFU}"
./gradlew testCheckerInferenceScript
./gradlew testCheckerInferenceDevScript

./gradlew test  -Pemit.test.debug=true

echo "Exiting checker-framework-inference/.travis-build.sh in" `pwd`
