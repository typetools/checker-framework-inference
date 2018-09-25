#!/bin/bash

WORKING_DIR=$(cd $(dirname "$0") && pwd)

JSR308=$(cd $WORKING_DIR/../../../ && pwd)

DLJC=$JSR308/do-like-javac

( cd $WORKING_DIR && \
$DLJC/dljc -t inference \
    --guess --crashExit \
    --checker dataflow.DataflowChecker \
    --solver checkers.inference.solver.SolverEngine \
    --solverArgs="solvingStrategy=Graph,solver=MaxSat" \
    --mode ROUNDTRIP -o $WORKING_DIR/logs \
    -afud $WORKING_DIR/annotated -- ant compile-project )
