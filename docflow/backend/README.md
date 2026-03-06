# DocFlow Backend

Spring Boot 3.x application targeting Oracle 19c.

## Features
- REST API with maker–checker workflow foundations.
- Liquibase-driven schema management.
- Pluggable storage adapters with filesystem default.
- Dynamic Reports module with admin template configuration and end-user execution.

## Getting Started
```bash
mvn spring-boot:run
```

## Reports Module (Current Behavior)

This section documents how Reports works **today** in this backend.

### 1) What the Reports Template module does

The template module lets admins define reusable report templates through `/api/reports` endpoints:
- base entity (main source table)
- output columns
- filters (key, type, operator policy, mode)

Key admin endpoints:
- `GET /api/reports/admin/scope` → available entities/columns/metadata keys
- `POST /api/reports/templates` → save template
- `PUT /api/reports/templates/{id}` → update template
- `GET /api/reports/templates` → list templates

### 2) What the end-user Reports module does

The execution module exposes template-focused endpoints so users run only preconfigured filters:
- `GET /api/reports/templates?mode=exec` → executable template list
- `GET /api/reports/templates/{id}` → filter contract for one template
- `POST /api/reports/run?mode=exec` → run template with user filter values

The backend returns rows/columns and handles paging.

### 3) Report configuration storage

#### Current implementation

Report templates are persisted in the `REPORT_TEMPLATES` table (`name`, `config_json`, metadata columns).
`config_json` stores the dynamic report request payload (base entity, columns, filters).

#### About APP_CONFIG

`APP_CONFIG` is used in this system for other config domains. The Reports runtime in this codebase currently does **not** read/write report templates from an `APP_CONFIG` row keyed by `TYPE=REPORTS` + `CONFIG_VALUE`; it uses `REPORT_TEMPLATES` instead.

If your deployment uses a different branch/customization that stores Reports config in `APP_CONFIG`, document that separately and keep this README aligned with deployed code.

### 4) Supported data sources

Reports can combine/filter using three sources:
- `DOCUMENT` (document parent table / known relational columns)
- `DOCUMENT_METADATA` (key/value metadata table, values stored text/CLOB)
- `THIRD_PARTY_ENTITY` (runtime-discovered relational table)

### 5) Third-party table resolution

`THIRD_PARTY_ENTITY` behavior is dynamic:
- base table names are configured under `docflow.reports.entities` in `application.yml`
- report metadata service inspects Oracle `USER_TAB_COLUMNS` at runtime
- code validates selected columns/operators against discovered metadata

No third-party schema is hardcoded in Java entities for reports.

### 6) Supported filter logical types

Current logical types:
- `STRING`
- `NUMBER`
- `DATE`

### 7) Supported operators

Current operator matrix:
- `STRING` → `EQ`, `LIKE`
- `NUMBER` → `EQ`, `LT`, `GT`
- `DATE` → `EQ`, `LT`, `GT`

`LIKE` semantics are intentionally controlled as **contains**.
Backend wraps user input as `%value%` and binds as SQL parameter.

### 8) How admin configures filters in templates

For each filter, template JSON stores contract-like data including:
- key (column or metadata key)
- source (`DOCUMENT` / `DOCUMENT_METADATA` / `THIRD_PARTY_ENTITY`)
- field
- logical type (`STRING` / `NUMBER` / `DATE`)
- allowed operators (`EQ`, `LIKE`, `LT`, `GT` depending on type)
- mode (`FIXED_VALUE` or `USER_INPUT`)

Admin scope API provides selectable entities/columns/metadata keys to drive UI choices.

### 9) End-user report execution behavior

At execution time, user-facing filter behavior is type-driven:
- `STRING`: text input, operators from template (`=` and Contains in UI labels)
- `NUMBER`: numeric input, operators `=`, `<`, `>`
- `DATE`: date input (`yyyy-MM-dd`), operators `=`, `<`, `>`

Blank values are skipped (no predicate generated).

### 10) Backend validation behavior

Execution service enforces:
- template/filter key must exist
- operator must be in filter allow-list
- duplicate input keys rejected
- blank value ignored
- non-blank value validated by type:
  - `NUMBER` must parse strict numeric format
  - `DATE` must parse strict `yyyy-MM-dd`

### 11) Query generation behavior (high level)

`DynamicReportBuilder` builds SQL with typed, source-aware predicates:
- `DOCUMENT` / relational columns:
  - `EQ`, `LT`, `GT` map to direct comparators
  - `LIKE` on string columns uses `LOWER(column) LIKE :param`
- `DOCUMENT_METADATA`:
  - string compares via metadata value expression
  - numeric/date use conversion (`TO_NUMBER`, `TO_DATE`) before compare
  - string `LIKE` uses contains pattern with parameter binding
- `THIRD_PARTY_ENTITY`:
  - joins and columns validated from runtime metadata
  - typed predicates applied same as relational source

### 12) Current limitations / out of scope

Not supported yet:
- `DATETIME`
- `BETWEEN`
- date presets / quick ranges
- regex-style operators
- starts-with / ends-with operator variants

### 13) Simple filter behavior examples

1. `Branch` (`STRING`, `LIKE`) with value `avi`
   - backend binds `%avi%`
   - matches values like `Avadi`, `Ravikumar`, `Navi Mumbai`

2. `Loan Amount` (`NUMBER`, `GT`) with value `50000`
   - backend applies numeric compare (`> 50000`), not lexical string compare

3. `Application Date` (`DATE`, `LT`) with value `2026-03-01`
   - backend applies date compare using canonical `yyyy-MM-dd`

4. Blank `Branch` value
   - filter ignored, query runs without that predicate

---

## Sample Upload Field Definition
- `src/test/resources/sample-upload-fields.json` contains a ready-to-import `UPLOAD_FIELDS` payload with text, date, number, select, and dropdown examples.

## Postman Collection
- Import `../ops/postman/docflow-workflow.postman_collection.json` to exercise configuration, upload, metadata updates, audit retrieval, and status transitions. Populate the `sampleFilePath` variable with a local file before invoking the upload request.

## Tests
- Execute integration tests (requires Docker access for Testcontainers):
  ```bash
  mvn test
  ```
