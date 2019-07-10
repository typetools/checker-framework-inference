#!/bin/bash
ROOT=$TRAVIS_BUILD_DIR/..

echo Entering `pwd`/.travis-build.sh, GROUP=$1

# Optional argument $1 is one of:
#   all, all-tests, jdk.jar, checker-framework-inference, downstream, misc, plume-lib
# It defaults to "all".
export GROUP=$1
if [[ "${GROUP}" == "" ]]; then
  export GROUP=all
fi

if [[ "${GROUP}" != "all" && "${GROUP}" != "all-tests" && "${GROUP}" != "misc" ]]; then
  echo "Bad argument '${GROUP}'; should be omitted or one of: all, all-tests, misc."
  exit 1
fi

# Fail the whole script if any command fails
set -e

export SHELLOPTS

echo "In checker-framework-inference/.travis-build.sh GROUP=$GROUP"

source ./.travis-build-without-test.sh

if [[ "${GROUP}" == "all-tests" || "${GROUP}" == "all" ]]; then
    ./gradlew testCheckerInferenceScript
    ./gradlew testCheckerInferenceDevScript

    ./gradlew test
fi

if [[ "${GROUP}" == "misc" || "${GROUP}" == "all" ]]; then
  set -e

  # Code style and formatting
  # TODO: enable checks once the code was reformatted
  # ./gradlew checkBasicStyle checkFormat --console=plain --warning-mode=all
fi

echo "Exiting checker-framework-inference/.travis-build.sh in" `pwd`
