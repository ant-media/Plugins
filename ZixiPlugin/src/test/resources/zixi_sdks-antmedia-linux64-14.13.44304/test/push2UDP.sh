#!/bin/bash
BASEDIR=$(dirname "$0")
cd $BASEDIR
ffmpeg -re -i test.flv -codec copy -f mpegts udp://127.0.0.1:1234?pkt_size=1316

