#!/bin/bash
BASEDIR=$(dirname "$0")
cd $BASEDIR
pwd

export LD_LIBRARY_PATH=../lib
./client_interface_tester 127.0.0.1:2077/stream1 1000 -1 127.0.0.1 5678

