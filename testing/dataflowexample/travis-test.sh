#!/bin/bash

set -e

WORKING_DIR=$(cd $(dirname "$0") && pwd)

JSR308=$(cd $WORKING_DIR/../../../ && pwd)

export AFU=$JSR308/annotation-tools/annotation-file-utilities
export LINGELING=$JSR308/lingeling

export PATH=$LINGELING:$AFU/scripts:$PATH

## Build libs for test
(cd $WORKING_DIR && ant compile-libs)

# test using basic dataflow solver
echo -e "\nRunning DataflowSolver\n"
$WORKING_DIR/runDataflowSolver.sh
$WORKING_DIR/cleanup.sh

# test using maxsat (internal) solver
echo -e "\nRunning MaxSatSolver\n"
$WORKING_DIR/runMaxSatSolver.sh
$WORKING_DIR/cleanup.sh

# test using lingeling (external) solver
echo -e "\nRunning LingelingSolver\n"
$WORKING_DIR/runLingelingSolver.sh
$WORKING_DIR/cleanup.sh
