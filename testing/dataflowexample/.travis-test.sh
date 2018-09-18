#!/bin/bash

set -e

WORKING_DIR=$(cd $(dirname "$0") && pwd)

JSR308=$(cd $WORKING_DIR/../../../ && pwd)

$WORKING_DIR/dataflow-setup.sh

# test using basic dataflow solver
$WORKING_DIR/runDataflowDFSolver.sh
$WORKING_DIR/cleanup.sh

# test using maxsat (internal) solver
$WORKING_DIR/runDataflowMaxSat.sh
$WORKING_DIR/cleanup.sh

# test using lingeling (external) solver
$WORKING_DIR/runDataflowLingeling.sh
$WORKING_DIR/cleanup.sh
