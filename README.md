# kafkamate (KM8)

Web GUI Kafka Tool to consume and produce messages.

Currently supports reading messages in STRING and PROTOBUF formats.

### Run kafkamate
```bash
➜  docker run -d --net host -v /your/path/to/kafkamate.json:/kafkamate.json csofronia/kafkamate:latest
```
Now go to your browser and access http://localhost:8080. That's it! :rocket:

This mount `-v /your/path/to/kafkamate.json:/kafkamate.json` is needed if you want to persist your kafka cluster configuration.
If this is skipped then it will start with no configuration. 

### Run locally
Start the site with (make sure to have already installed `npm` and add env `export NODE_OPTIONS=--openssl-legacy-provider` if errors pop up):
```bash
➜ sbt dev
``` 

In another shell tab start the service:
```bash
➜ sbt service/run
➜ # or: sbt service/reStart
```

We need also to start the Envoy proxy to forward the browser's gRPC-Web requests to the backend:
```bash
➜ # if you're using linux
➜ docker run --rm -d --net host -v "$(pwd)"/build/envoy.yaml:/etc/envoy/envoy.yaml:ro envoyproxy/envoy:v1.15.0
➜ # if you're using macos, then try the next one
➜ docker run --platform linux/amd64 --rm -p 61234:61234 --add-host host.docker.internal:192.168.0.114 -v "$(pwd)"/build/envoy.yaml:/etc/envoy/envoy.yaml:ro envoyproxy/envoy:v1.15.0 -c /etc/envoy/envoy.yaml -l debug
➜ # if you're using windows, then try the next one
➜ docker run --rm -d -p 61234:61234 --add-host host.docker.internal:192.168.0.114 -v %cd%\build\envoy.yaml:/etc/envoy/envoy.yaml:ro envoyproxy/envoy:v1.15.0 -c /etc/envoy/envoy.yaml -l debug
```

### Build docker image
```bash
➜ sbt dockerize
```


### KAFKA TRADEMARK DISCLAIMER
KAFKA is a registered trademark of The Apache Software Foundation and
has been licensed for use by kafkamate. kafkamate has no
affiliation with and is not endorsed by The Apache Software Foundation.
