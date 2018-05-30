#!/bin/bash
ROOT=$TRAVIS_BUILD_DIR/..

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

if [[ "${GROUP}" != "cfi-tests" && "${GROUP}" != "downstream" && "${GROUP}" != "all" ]]; then
  echo "Bad argument '${GROUP}'; should be omitted or one of: cfi-tests, downstream, all."
  exit 1
fi

. ./.travis-build-without-test.sh

# Test CF Inference
if [[ "${GROUP}" == "cfi-tests" || "${GROUP}" == "all" ]]; then
    ./gradlew testCheckerInferenceScript
    ./gradlew testCheckerInferenceDevScript

    ./gradlew test
fi

# Downstream tests
if [[ "${GROUP}" == "downstream" || "${GROUP}" == "all" ]]; then

  # Only perform ontology downstream test in opprop.
  if [[ "${SLUGOWNER}" == "opprop" ]]; then
    # Ontology test: 10 minutes
    echo "Running: (cd .. && git clone --depth 1 https://github.com/opprop/ontology.git)"
    (cd .. && git clone --depth 1 https://github.com/opprop/ontology.git)
    echo "... done: (cd .. && git clone --depth 1 https://github.com/opprop/ontology.git)"

    echo "Running: (cd ../ontology && gradle build -x test && ./test-ontology.sh)"
    (cd ../ontology && gradle build -x test && ./test-ontology.sh)
    echo "... done: (cd ../ontology && gradle build -x test && ./test-ontology.sh)"
  fi
fi
