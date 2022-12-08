#!/bin/bash
BASEDIR=$(dirname "$0")
cd $BASEDIR
pwd

export LD_LIBRARY_PATH=../lib
./feeder_interface_tester 1234 127.0.0.1 2088 stream1 1000 10000 0 -1 0
