# RabbitMQ Chat CLI

This is a bare simple CLI to manipulate RabbitMQ Client and API

![asciinema demo](./assets/demo.gif)

## Dependencies

- [Java 21](https://www.oracle.com/br/java/technologies/downloads/)
- [Maven](https://maven.apache.org/)

## Usage

```bash
# Configure RabbitMQ instance credentials
$EDITOR .env
# Build
mvn clean compile assembly:single
# Run
java -jar target/ChatRabbitMQ-1.0-SNAPSHOT-jar-with-dependencies.jar
```
