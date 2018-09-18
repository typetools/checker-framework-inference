#!/bin/bash

WORKING_DIR=$(cd $(dirname "$0") && pwd)

cd $WORKING_DIR

rm -rf logs
rm -rf annotated
rm -rf cnfData
