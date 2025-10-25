# DocFlow Runbook

## Requirements
- Java 17 or newer (the build compiles with `--release 17`, so any JRE ≥ 17 can run the service)
- Node.js 20
- Oracle Database 19c running and accessible on port 1522

## Environment Variables
Configure the following variables for both local development and deployment:
- `ORACLE_USER`
- `ORACLE_PASSWORD`
- `ORACLE_SID`
- `ORACLE_PORT`
- `ORACLE_HOST`

## Backend
Build and run the Spring Boot service:
```bash
mvn clean package
java -jar target/backend.jar
```

## Frontend
Install dependencies and start the Vite development server:
```bash
npm install
npm run dev
```

## Docker Compose Stack
Use Docker Compose to launch the full stack, including Oracle XE:
```bash
docker-compose up
```

To build the backend container with a different JRE (for example Java 23), export `DOCFLOW_BACKEND_JAVA_MAJOR=23` before running `docker-compose build` or `docker-compose up --build`. If you need to align the build stage JDK, set `DOCFLOW_BACKEND_MAVEN_TAG` (e.g. `DOCFLOW_BACKEND_MAVEN_TAG=3.9.6-eclipse-temurin-23`).

## Authentication
DocFlow uses header-based authentication only. Each request must include `X-USER-ID`.

Available users:
- `maker1`
- `reviewer1`
- `checker1`
- `admin1`

## Storage Configuration
The default storage adapter is the filesystem adapter, configured via `docflow.storage.type=filesystem`.

- Files are stored under `./uploads/{year}/{month}`.
- An `oracle-db` storage adapter stub is included for future enhancement.

## Troubleshooting
- **Liquibase syntax errors**: Validate change logs with `mvn liquibase:validate` and ensure Oracle-specific data types are used.
- **Oracle JDBC driver issues**: Verify the Oracle driver is available in the local Maven repository or configured via Oracle's Maven repository.
- **File permission errors**: Ensure the process has read/write access to the filesystem upload directory.
