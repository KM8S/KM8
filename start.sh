#!/bin/bash

service nginx start
envoy -c envoy.yaml &
java -jar $1