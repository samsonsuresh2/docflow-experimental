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

#### About JSON_CONFIG

`JSON_CONFIG` is used in this system for other config domains. The Reports runtime in this codebase currently does **not** read/write report templates from a `JSON_CONFIG` row keyed by `TYPE=REPORTS` + `CONFIG_VALUE`; it uses `REPORT_TEMPLATES` instead.

If your deployment uses a different branch/customization that stores Reports config in `JSON_CONFIG`, document that separately and keep this README aligned with deployed code.

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

## Date Presets for Report Filters

### Overview

The **Date Preset** feature allows reports to support commonly used date ranges (e.g., *This Week*, *Previous Week*, *This Month*) without requiring users to manually enter date values. Presets are **configuration-driven**, stored in the database, and resolved by the backend before the report query executes.

The design ensures:

* Presets apply **only to DATE-type filters**
* Presets are **defined by backend/app teams**, not end users
* Each DATE filter in a report can **independently support presets**
* The report query engine still receives **normal date ranges**, keeping existing query logic unchanged.

---

### Core Concept

A preset does **not execute a query directly**.
It simply resolves into a **date range (from / to)**.

Example:

Preset selected by user:

```text
PREVIOUS_WEEK
```

Backend resolves:

```text
from = 2026-02-23
to   = 2026-03-01
```

Report query then applies the filter normally:

```text
dispatch_date BETWEEN from AND to
```

## Reports Range Enhancement

Reports now support bounded interval filters in addition to the existing single-value comparisons.

- `STRING` supports `EQ`, `LIKE`
- `NUMBER` supports `EQ`, `LT`, `GT`, `RANGE`
- `DATE` supports `EQ`, `LT`, `GT`, `BETWEEN`

Execution behavior:

- `NUMBER RANGE` uses two values: `valueFrom` and `valueTo`
- `DATE BETWEEN` uses two values: `valueFrom` and `valueTo`
- single-value operators continue to use `value`
- blank single-value filters are skipped
- incomplete `RANGE` / `BETWEEN` inputs are rejected
- bounded filters are inclusive and require `valueFrom <= valueTo`

Query behavior:

- relational `DATE` and `TIMESTAMP` columns are treated as date-only for current `DATE` filters
- metadata number/date filters continue to use typed conversion before comparison
- `DOCUMENT`, `DOCUMENT_METADATA`, and `THIRD_PARTY_ENTITY` all support the new bounded operators

---

### Database Configuration

#### 1. `date_preset_master`

Stores reusable preset definitions.

Example fields:

* `preset_code`
* `preset_name`
* `preset_description`
* `start_rule`
* `end_rule`
* `enabled`
* `display_order`

Example rows:

| preset_code    | start_rule           | end_rule           |
| -------------- | -------------------- | ------------------ |
| THIS_WEEK      | CURRENT_WEEK_START   | TODAY              |
| PREVIOUS_WEEK  | PREVIOUS_WEEK_START  | PREVIOUS_WEEK_END  |
| THIS_MONTH     | CURRENT_MONTH_START  | TODAY              |
| PREVIOUS_MONTH | PREVIOUS_MONTH_START | PREVIOUS_MONTH_END |

---

#### 2. `report_filter_preset_map`

Maps presets to specific report filters.

Example fields:

* `report_code`
* `filter_key`
* `preset_code`
* `enabled`
* `display_order`

Example mapping:

| report_code | filter_key    | preset_code   |
| ----------- | ------------- | ------------- |
| LOAN_REPORT | disbursalDate | THIS_WEEK     |
| LOAN_REPORT | disbursalDate | PREVIOUS_WEEK |
| LOAN_REPORT | postingDate   | THIS_MONTH    |

This mapping determines **which presets appear for which filter in a report**.

---

### How Presets Are Created

Preset definitions are **inserted by backend/application teams**.

Typical method:

* Added via **Liquibase/Flyway migration scripts**
* Inserted into `date_preset_master`
* Mapped to report filters via `report_filter_preset_map`

No code change is required to introduce a new preset.

---

### Supported Preset Rules

Preset rules use a controlled vocabulary such as:

```text
TODAY
CURRENT_WEEK_START
CURRENT_WEEK_END
PREVIOUS_WEEK_START
PREVIOUS_WEEK_END
CURRENT_MONTH_START
CURRENT_MONTH_END
PREVIOUS_MONTH_START
PREVIOUS_MONTH_END
PREVIOUS_<DAY_OF_WEEK>
CURRENT_<DAY_OF_WEEK>
```

Optional day offsets may also be supported (e.g., anchor − 7 days).

Example:

```text
start_rule = PREVIOUS_WEDNESDAY
end_rule   = TODAY
```

---

### How App Teams Use It

1. Insert a new preset definition in `date_preset_master`.
2. Map the preset to the desired report filter using `report_filter_preset_map`.
3. Ensure the report filter is a **DATE logical type** and preset-enabled.

Example workflow:

```text
Add preset: PREVIOUS_WEDNESDAY_TO_TODAY
Map to: LOAN_DISBURSAL_REPORT → disbursalDate
```

Once deployed, the preset automatically becomes available in the report UI.

---

### How Users Use Presets

For each DATE filter that supports presets, users can choose between:

#### Manual Mode

User enters a date range manually:

```text
From Date
To Date
```

#### Preset Mode

User selects a preset:

```text
This Week
Previous Week
This Month
```

Each DATE filter operates **independently**, so multiple presets can be used in one report.

Example:

```text
Disbursal Date → Previous Week
Posting Date   → This Month
```

---

### Execution Flow

1. UI sends report request with filter modes.
2. Backend checks each DATE filter.
3. If filter mode = `PRESET`, the **Preset Resolver** converts the preset into actual dates.
4. Filters are normalized into `from / to` values.
5. Existing report query builder executes normally.

---

### Design Principles

* Presets are **metadata-driven**, not hardcoded.
* Backend is the **source of truth** for date resolution.
* Query builder receives only **normalized date ranges**.
* Multiple date filters can use presets simultaneously.
* New presets can be added **without code changes**.

---

### Current Scope

Supported:

* DATE filters only
* reusable preset catalog
* backend resolution
* multiple preset-enabled filters in a report

Not supported:

* presets affecting non-date fields
* holiday calendar logic
* conditional branching rules
* user-defined runtime formulas

---

### Summary

Date Presets provide a **configurable and reusable mechanism** for common report date windows.
They improve user experience while keeping the reporting engine simple by converting presets into standard date ranges before query execution.

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
