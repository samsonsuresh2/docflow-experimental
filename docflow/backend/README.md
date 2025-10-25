# DocFlow Backend

Spring Boot 3.x application targeting Oracle 19c.

## Features
- REST API with maker–checker workflow foundations.
- Liquibase-driven schema management.
- Pluggable storage adapters with filesystem default.

## Getting Started
Ensure you are using Java 17 or a newer JDK (the project is compiled with `--release 17` and runs on any JRE ≥ 17).
```bash
mvn spring-boot:run
```

### Container builds with alternative Java versions

The backend Dockerfile accepts build arguments to choose the JDK/JRE images. For example, to run with a Java 23 runtime:

```bash
docker build --build-arg RUNTIME_JAVA_MAJOR=23 --build-arg MAVEN_JDK_TAG=3.9.6-eclipse-temurin-23 -t docflow-backend .
```

## Sample Upload Field Definition
- `src/test/resources/sample-upload-fields.json` contains a ready-to-import `UPLOAD_FIELDS` payload with text, date, number, select, and dropdown examples.

## Postman Collection
- Import `../ops/postman/docflow-workflow.postman_collection.json` to exercise configuration, upload, metadata updates, audit retrieval, and status transitions. Populate the `sampleFilePath` variable with a local file before invoking the upload request.

## Tests
- Execute integration tests (requires Docker access for Testcontainers):
  ```bash
  mvn test
  ```
