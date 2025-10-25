# DocFlow Backend

Spring Boot 3.x application targeting Oracle 19c.

## Features
- REST API with maker–checker workflow foundations.
- Liquibase-driven schema management.
- Pluggable storage adapters with filesystem default.

## Getting Started
```bash
mvn spring-boot:run
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
