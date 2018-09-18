#!/bin/bash

WORKING_DIR=$(cd $(dirname "$0") && pwd)

JSR308=$(cd $WORKING_DIR/../../../ && pwd)

DLJC=$JSR308/do-like-javac

cd $WORKING_DIR

# --crashExit

$DLJC/dljc -t inference \
    --guess \
    --checker dataflow.DataflowChecker \
    --solver dataflow.solvers.classic.DataflowSolver \
    --mode ROUNDTRIP -o $WORKING_DIR/logs \
    -afud $WORKING_DIR/annotated -- ant compile-project

cat ./logs/infer.log
