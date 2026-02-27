# DocFlow

DocFlow is a single-tenant document review and approval portal featuring a maker–checker workflow, dynamic metadata configuration, and BFSI-compliant audit capabilities.

## Structure

- `backend/` — Spring Boot service with Oracle integration, Liquibase migrations, and pluggable storage adapters.
- `frontend/` — React + TypeScript client rendered via Vite and Tailwind CSS.
- `ops/` — Docker Compose setup for orchestrating the backend, frontend, and Oracle database locally.

Refer to component-level READMEs for setup and usage details.

## Authorization UX

The UI now hides modules the active role cannot access. Module visibility is derived from the `allowedModules` list returned by `/api/auth/me`, while the backend continues to enforce authorization for every request.

## Workflow permissions

Workflow permissions are DB-driven via `WORKFLOW_ACTIONS` and `ROLE_WORKFLOW_ACTION_ACCESS`. The backend enforces allowed actions and returns `allowedActions` per document so the UI can render workflow buttons accordingly.

## JSON Schema Versioning & Sandbox Mode

This feature exists to keep document processing stable when schema rules change.

Previously, changing the schema could affect all documents, including older ones that were already in progress. Now, each document is tied to the schema version that was active when it was created. This prevents older documents from breaking when newer schema updates are introduced.

### Schema States

- **SANDBOX**: Used to test schema changes in lower environments (such as SIT/UAT). This state is editable.
- **ACTIVE**: The current production-ready schema version. This state is not editable.
- **DEPRECATED**: Older schema versions kept for validating historical documents. This state is not editable.

### How Sandbox Mode Works

In testing environments, new documents automatically use the SANDBOX schema.

Admins can update the sandbox schema repeatedly, and test documents reflect those updates immediately. This allows teams to run full end-to-end workflow testing (create → review → approve) safely before release.

### How Promotion Works

When sandbox testing is complete, the SANDBOX schema can be promoted.

Promotion creates a new ACTIVE version, and the previous ACTIVE version is marked as DEPRECATED. ACTIVE versions remain read-only after promotion.

### Production Behavior

In production, new documents always use the ACTIVE schema version.

Each document continues using the schema version it was created with for its full lifecycle. Future schema changes do not affect existing documents.

### Configuration Note

Application configuration controls the operating mode:

- **SANDBOX_ONLY**: Intended for SIT/UAT testing.
- **ACTIVE_ONLY**: Intended for production.
