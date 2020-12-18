#!/bin/bash

set -m

# Start nginx for site
service nginx start

# Start envoyproxy
envoy -c envoy.yaml &

# Start backend for service
java -jar $1