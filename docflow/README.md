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
