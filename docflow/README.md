# DocFlow

DocFlow is a single-tenant document review and approval portal featuring a maker–checker workflow, dynamic metadata configuration, and BFSI-compliant audit capabilities. The backend targets Java 17 and runs on any modern JRE version 17 or higher.

## Structure

- `backend/` — Spring Boot service with Oracle integration, Liquibase migrations, and pluggable storage adapters.
- `frontend/` — React + TypeScript client rendered via Vite and Tailwind CSS.
- `ops/` — Docker Compose setup for orchestrating the backend, frontend, and Oracle database locally.

Refer to component-level READMEs for setup and usage details.
