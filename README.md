# kafkamate

Web GUI Kafka Tool to consume and produce messages (WIP)


### Run locally
Start the site with:
```bash
➜ sbt dev
``` 

In another shell tab start the service:
```bash
➜ sbt service/run
```

We need also to start the Envoy proxy to forward the browser's gRPC-Web requests to the backend:
```bash
➜ docker run -d -v "$(pwd)"/common/src/main/resources/envoy.yaml:/etc/envoy/envoy.yaml:ro --network=host envoyproxy/envoy:v1.15.0
```