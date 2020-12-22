# kafkamate

Web GUI Kafka Tool to consume and produce messages (WIP)

### Run kafkamate
```bash
➜  docker run -d --network host -v /your/path/to/kafkamate.json:/kafkamate.json csofronia/kafkamate:latest
```
Now go to your browser and access `http://localhost:8080`. That's it! :rocket:

This mount `-v /your/path/to/kafkamate.json:/kafkamate.json` is needed if you want to persist your cluster configuration.
If this is skipped then it will start with no configuration. 

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
➜ docker run -d -v "$(pwd)"/build/envoy.yaml:/etc/envoy/envoy.yaml:ro --network host envoyproxy/envoy:v1.15.0
```