# tracing-demo
To run this demo:
- Start the application
```shell
./mvnw spring-boot:run
java -jar target/tracing-demo-1.0.0-SNAPSHOT.jar
```

- start grafana stack with `docker compose up`
```shell
docker compose up
```

- create a native executable
```shell
./mvnw native:compile -Pnative
```
- To run the native executable
```shell
  ./target/tracing-demo-1.0.0-SNAPSHOT-runner
```