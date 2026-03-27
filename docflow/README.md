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

DocFlow now versions upload JSON schema so existing documents remain stable even when admins evolve the schema later.

- **SANDBOX** (`schema_version=0`): draft lane for schema edits and end-to-end lifecycle testing in SIT/UAT.
- **ACTIVE** (`schema_version>=1`): released immutable lane used by production document creation.
- **DEPRECATED**: older immutable active releases preserved for historical document validation.

### End-to-end sandbox testing flow
1. Admin saves updates into SANDBOX (editable).
2. New documents bind according to server strategy:
   - `SANDBOX_ONLY`: new docs bind as `FLOATING_SANDBOX` + version `0`.
   - `ACTIVE_ONLY`: new docs bind as `FIXED_VERSION` + current active version.
3. Document lifecycle validation always uses the bound schema:
   - floating docs re-read current sandbox schema
   - fixed docs always use their stored active version

### Promotion flow
- Admin promotes SANDBOX to a new ACTIVE release.
- Promotion copies SANDBOX JSON into a brand-new ACTIVE row with incremented version.
- Previous ACTIVE becomes DEPRECATED.
- SANDBOX remains editable for future drafts.

### Production behavior
Set `schema.bindingStrategy=ACTIVE_ONLY` (default). In this mode document creation fails until an ACTIVE schema exists.

### Production release flow
- In `ACTIVE_ONLY`, the current ACTIVE schema remains read-only.
- Admins release the next approved JSON through the Admin UI using `Release New Version`.
- The backend validates the supplied JSON, creates a brand-new ACTIVE row with the next version number, and marks the prior ACTIVE row as DEPRECATED.
- Existing documents stay bound to their stored schema version, while newly created documents bind to the new ACTIVE version.

### Configuration
`application.yml`
```yaml
schema:
  bindingStrategy: ACTIVE_ONLY # or SANDBOX_ONLY
```
