#!/bin/bash

set -m

# Set FE configuration
echo 'window.KM8Config = { BE_HOST: "'${KM8_BE_HOST}'" };' > /usr/share/nginx/html/config.js

# Start nginx for site
service nginx start

# Start envoyproxy
envoy -c envoy.yaml &

# Start backend for service
java -jar $1