#!/bin/bash

echo "Entering checker-framework-inference/.travis-build.sh in" `pwd`

# Fail the whole script if any command fails
set -e

export SHELLOPTS

# Optional argument $1 is one of:
#   cfi-tests, downstream
# If it is omitted, this script does everything.
export GROUP=$1
if [[ "${GROUP}" == "" ]]; then
  export GROUP=all
fi

SLUGOWNER=${TRAVIS_REPO_SLUG%/*}
if [[ "$SLUGOWNER" == "" ]]; then
  SLUGOWNER=opprop
fi

if [[ "${GROUP}" != "cfi-tests" && "${GROUP}" != downstream* && "${GROUP}" != "all" ]]; then
  echo "Bad argument '${GROUP}'; should be omitted or one of: cfi-tests, downstream-*, all."
  exit 1
fi

. ./.travis-build-without-test.sh

# Test CF Inference
if [[ "${GROUP}" == "cfi-tests" || "${GROUP}" == "all" ]]; then
    ./gradlew testCheckerInferenceScript
    ./gradlew testCheckerInferenceDevScript

    ./gradlew test

    ./gradlew testDataflowExternalSolvers
fi

# Downstream tests
# Only perform downstream test in opprop.
if [[ "${GROUP}" == downstream* && "${SLUGOWNER}" == "opprop" ]]; then

    # clone_downstream Git_Target Git_Branch
    clone_downstream () {
        COMMAND="git clone -b $2 --depth 1 https://github.com/opprop/$1.git"
        echo "Running: (cd .. && $COMMAND)"
        (cd .. && eval $COMMAND)
        echo "... done: (cd .. && $COMMAND)"
    }

    # test_downstream Git_Target Build_Test_Command
    test_downstream() {
        COMMAND="cd ../$1 && ${@:2}"
        echo "Running: ($COMMAND)"
        (eval $COMMAND)
        echo "... done: ($COMMAND)"
    }

    # Ontology test
    if [[ "${GROUP}" == "downstream-ontology" ]]; then
        ONTOLOGY_GIT=ontology
        ONTOLOGY_BRANCH=master
        ONTOLOGY_COMMAND="./gradlew build -x test && ./test-ontology.sh"

        clone_downstream $ONTOLOGY_GIT $ONTOLOGY_BRANCH
        test_downstream $ONTOLOGY_GIT $ONTOLOGY_COMMAND
    fi

    # Units test
    if [[ "${GROUP}" == "downstream-units" ]]; then
        UNITS_GIT=units-inference
        UNITS_BRANCH=master
        UNITS_COMMAND="./gradlew build -x test && ./test-units.sh"

        clone_downstream $UNITS_GIT $UNITS_BRANCH
        test_downstream $UNITS_GIT $UNITS_COMMAND
    fi
fi

echo "Exiting checker-framework-inference/.travis-build.sh in" `pwd`
