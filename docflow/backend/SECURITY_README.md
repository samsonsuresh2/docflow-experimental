# DocFlow Security README

## 1. Purpose

This security track was introduced to make DocFlow ready for enterprise deployment and client SSO onboarding.

It adds a configurable authentication foundation, backend API authentication enforcement, controlled local/dev access, OIDC/JWT support, CORS controls, actuator exposure control, security response headers, clean JSON `401` responses, and focused security tests.

The main design goal is:

> Client SSO should prove identity, while DocFlow internally controls application roles/modules.

In practical terms, the client identity provider authenticates the user. DocFlow then uses its own internal user-role and module-access model to decide what the authenticated user can do inside the application.

## 2. What Was In Scope

The implemented security scope covers:

- `DEV_AUTH` for local development and demos.
- `HEADER_AUTH` for controlled gateway or SIT-style testing.
- `OIDC_AUTH` for real enterprise SSO.
- JWT validation for OIDC bearer tokens.
- JWT signature validation through a configured JWK set URI.
- Issuer validation through a configured issuer URI.
- Optional audience validation when `docflow.security.oidc.audience` is configured.
- Configurable user and email claim mapping.
- Ignoring IdP roles/groups for DocFlow authorization.
- Resolving DocFlow application roles from `UserRoleService`.
- Preserving internal `ROLE_MODULE_ACCESS` based authorization.
- Backend authentication enforcement for protected APIs.
- Gated `/api/auth/dev-login` behavior.
- JSON `401` responses from the authentication filter.
- Centralized CORS configuration.
- Security headers for API responses.
- Externalized actuator exposure.
- Test suite restoration and focused security tests.

## 3. What Was Out of Scope

The following areas are intentionally outside this security track:

- Full module/action authorization redesign.
- Data masking.
- Audit redesign.
- Rate limiting.
- WAF or API gateway setup.
- TLS certificate management.
- Enterprise IdP configuration ownership.
- Production infrastructure and network security.
- Advanced ABAC.
- IdP role/group based authorization.
- Business workflow changes.

These may still be valid future hardening areas, but they are not part of the current authentication foundation.

## 4. What Has Been Achieved

Current implemented authentication modes:

- `DISABLED`
- `DEV_AUTH`
- `HEADER_AUTH`
- `OIDC_AUTH`

Current behavior verified from code:

- Default auth mode is `DISABLED`.
- In `DISABLED`, protected APIs return JSON `401` with message `Authentication is not configured`.
- `OIDC_AUTH` requires `docflow.security.oidc.issuer-uri`.
- `OIDC_AUTH` requires `docflow.security.oidc.jwk-set-uri`.
- `docflow.security.oidc.audience` is optional.
- If audience is configured, JWT `aud` is validated.
- JWT audience validation supports both standard array/list `aud` and scalar string `aud`.
- OIDC clients must send `Authorization: Bearer <JWT>`.
- OIDC extracts user identity from the configured user ID claim.
- OIDC extracts email from the configured email claim when present.
- OIDC token roles/groups are ignored for DocFlow authorization.
- DocFlow application roles are resolved through `UserRoleService`.
- `UserRoleService` reads enabled internal roles and can apply the implicit `MAKER` role when `docflow.auth.implicitMakerEnabled` is true.
- `RequestUserContext` is populated after authentication and cleared at the start and end of request handling.
- `OPTIONS`, `/actuator/health`, and `/actuator/info` are public.
- `/api/auth/dev-login` is public only when `DEV_AUTH` is active and dev auth is allowed.
- Authentication failures return JSON:

```json
{
  "error": "Unauthorized",
  "message": "..."
}
```

CORS behavior verified from code:

- CORS is configured through `docflow.security.cors`.
- Empty `allowed-origins` does not allow arbitrary origins.
- Wildcard `*` is rejected when the active profile is `prod`, `production`, or `uat`.
- Default allowed headers include `Authorization`, `Content-Type`, `X-USER-ID`, `X-USER-EMAIL`, and `X-USER-ROLES`.

Security headers verified from code:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- `Cache-Control: no-store`
- `Pragma: no-cache`

Actuator behavior verified from configuration:

- Exposure is externalized through `DOCFLOW_ACTUATOR_EXPOSURE`.
- Packaged non-prod default is `health,info,loggers`.
- Recommended UAT/prod exposure is `health,info`.

Latest verification from the current branch:

- `mvn -q -DskipTests compile` passed.
- `mvn -q -DskipTests test-compile` passed.
- `mvn -q test` passed.
- Latest known result: 347 tests, 0 failures, 0 errors, 3 skipped.

## 5. Authentication Modes

### DISABLED

`DISABLED` is the default safe mode.

It prevents accidental insecure startup. If no explicit authentication mode is configured, protected APIs return JSON `401` instead of allowing unauthenticated access.

Use this as a safe default only. UAT and production should explicitly configure the required authentication mode, normally `OIDC_AUTH`.

### DEV_AUTH

`DEV_AUTH` is for local development and demos only.

It creates a development identity from configured values such as:

- `docflow.security.dev-user-id`
- `docflow.security.dev-user-email`
- `docflow.security.dev-user-roles`

Dev auth is allowed only when:

- the active Spring profile contains `local`, or
- `docflow.security.allow-dev-auth=true`

`/api/auth/dev-login` is public only when:

- `docflow.security.auth-mode=DEV_AUTH`, and
- dev auth is allowed by local profile or explicit config.

Do not use `DEV_AUTH` in UAT or production.

### HEADER_AUTH

`HEADER_AUTH` is for controlled gateway or SIT-style testing.

It reads identity from configured headers. Current defaults are:

- `docflow.security.header-user-id`: `X-USER-ID`
- `docflow.security.header-email`: `X-USER-EMAIL`
- `docflow.security.header-roles`: `X-USER-ROLES`

`X-USER-ID` is mandatory in this mode. Email and roles headers are optional.

Important: header authentication is not safe if the backend is directly reachable by browsers or untrusted clients. A user could spoof identity headers. Use this only behind a trusted gateway that strips inbound identity headers from clients and injects verified values.

DocFlow authorization still comes from internal role resolution. Header roles are not a substitute for DocFlow internal role/module configuration.

### OIDC_AUTH

`OIDC_AUTH` is the enterprise SSO mode.

It validates bearer JWTs from:

```text
Authorization: Bearer <JWT>
```

Current OIDC behavior:

- Requires `docflow.security.oidc.issuer-uri`.
- Requires `docflow.security.oidc.jwk-set-uri`.
- Validates JWT signature using the configured JWK set URI.
- Validates token expiry.
- Validates issuer.
- Validates audience only if `docflow.security.oidc.audience` is configured.
- Supports `aud` as either an array/list or scalar string.
- Extracts user identity from `docflow.security.oidc.user-id-claim`.
- Extracts email from `docflow.security.oidc.email-claim`.
- Ignores token roles/groups for DocFlow authorization.

DocFlow role/module access remains internal and is resolved through `UserRoleService` and the existing role/module access model.

## 6. Client Integration Checklist

Before UAT or production SSO onboarding, collect the following from the client IdP and infrastructure teams:

1. Issuer URI.
2. JWK set URI.
3. Audience value, if the IdP issues or expects one for DocFlow.
4. User ID claim name, for example `sub`, `preferred_username`, or another agreed claim.
5. Email claim name, for example `email`.
6. Confirmation that the backend receives:

```text
Authorization: Bearer <JWT>
```

7. Frontend URL/origin for the CORS allowlist.
8. UAT/prod actuator exposure requirement, normally `health,info`.
9. Network/API gateway behavior:

- Whether the `Authorization` header is forwarded unchanged.
- Whether the backend is shielded from the public internet.
- Whether any proxy strips, rewrites, or logs headers.

10. A sample decoded JWT payload without real secrets or real user data.

Example safe sample payload:

```json
{
  "iss": "https://idp.example.com/issuer",
  "sub": "user123",
  "email": "user123@example.com",
  "aud": "docflow",
  "exp": 1893456000
}
```

The client does not need to provide IdP roles/groups for the current DocFlow authorization design. IdP groups are currently not used for DocFlow authorization. Elevated DocFlow roles such as reviewer, approver, or admin are configured internally in DocFlow.

## 7. Required UAT/Production Configuration

Typical OIDC configuration:

```yaml
docflow:
  security:
    auth-mode: OIDC_AUTH
    oidc:
      issuer-uri: https://idp.example.com/issuer
      jwk-set-uri: https://idp.example.com/.well-known/jwks.json
      audience: docflow
      user-id-claim: sub
      email-claim: email
```

If the deployment does not require audience validation, omit `audience`:

```yaml
docflow:
  security:
    auth-mode: OIDC_AUTH
    oidc:
      issuer-uri: https://idp.example.com/issuer
      jwk-set-uri: https://idp.example.com/.well-known/jwks.json
      user-id-claim: sub
      email-claim: email
```

Typical CORS configuration:

```yaml
docflow:
  security:
    cors:
      allowed-origins:
        - https://docflow-client.example.com
      allowed-methods:
        - GET
        - POST
        - PUT
        - PATCH
        - DELETE
        - OPTIONS
      allowed-headers:
        - Authorization
        - Content-Type
        - X-USER-ID
        - X-USER-EMAIL
        - X-USER-ROLES
      allow-credentials: false
      max-age-seconds: 3600
```

Recommended UAT/prod actuator exposure:

```bash
DOCFLOW_ACTUATOR_EXPOSURE=health,info
```

Important production notes:

- Do not use wildcard CORS origins in UAT/prod.
- Do not use `DEV_AUTH` in UAT/prod.
- Do not use `HEADER_AUTH` unless the backend is protected by a trusted gateway and the setup is explicitly approved.
- Do not put real secrets, tokens, or private keys in application config.

## 8. Local Developer / Demo Mode

Developers do not need real client SSO for local development.

### Option A: DEV_AUTH

Use `DEV_AUTH` for local demos or quick backend/frontend development without an IdP.

Example local configuration:

```yaml
docflow:
  security:
    auth-mode: DEV_AUTH
    allow-dev-auth: true
    dev-user-id: local-user
    dev-user-email: local-user@example.com
    dev-user-roles:
      - MAKER
```

Alternatively, run with the local profile:

```bash
SPRING_PROFILES_ACTIVE=local
```

When allowed, `/api/auth/dev-login` can be called without authentication to support local identity/session-style flows. When `DEV_AUTH` is not active or not allowed, `/api/auth/dev-login` is not public.

Do not carry `allow-dev-auth=true` into UAT or production configuration.

### Option B: HEADER_AUTH

Use `HEADER_AUTH` for local API testing, Postman testing, or controlled SIT-style testing.

Example configuration:

```yaml
docflow:
  security:
    auth-mode: HEADER_AUTH
```

Default headers:

```text
X-USER-ID: local-user
X-USER-EMAIL: local-user@example.com
```

`X-USER-ROLES` may be sent by local tools, but DocFlow authorization should still be validated against internal DocFlow roles/module access. Do not treat client-supplied headers as trustworthy unless a trusted gateway owns them.

## 9. What Is Still Pending / Future Hardening

The current implementation provides the authentication and API-hardening foundation. Future hardening and deployment responsibilities include:

- Full module/action authorization coverage review.
- Upload validation limits and content checks.
- Rate limiting.
- HSTS, CSP, and Permissions-Policy if required by deployment standards.
- Production monitoring and logging policy.
- External gateway or WAF rules.
- Security sign-off with the client IdP team.
- Periodic penetration and security review.
- Review of all actuator exposure settings before production release.

These items do not mean the current authentication track is incomplete. They are normal follow-up controls for enterprise production readiness.

## 10. Troubleshooting

### 401: Authentication is not configured

Likely cause: `docflow.security.auth-mode` is missing or left as the default `DISABLED`.

Fix: Set an explicit mode such as `OIDC_AUTH`, `HEADER_AUTH`, or local-only `DEV_AUTH`.

### 401: Missing bearer token

Likely cause: `OIDC_AUTH` is enabled, but the request does not include `Authorization: Bearer <JWT>`.

Fix: Ensure the frontend or gateway forwards the `Authorization` header to the backend.

### 401: Invalid bearer token

Likely cause: token is expired, malformed, signed by an unexpected key, has the wrong issuer, has the wrong audience when audience is configured, or the JWK endpoint cannot validate it.

Fix: Check IdP issuer, JWK set URI, token expiry, and configured audience. Do not log or paste real JWTs into tickets.

### Invalid issuer

Likely cause: token `iss` does not match `docflow.security.oidc.issuer-uri`.

Fix: Confirm the exact issuer value from the IdP metadata and configure it exactly.

### Invalid audience

Likely cause: `docflow.security.oidc.audience` is configured, but the token `aud` does not contain that value.

Fix: Confirm the DocFlow audience/client value with the IdP team, or omit `audience` if the deployment does not require audience validation.

### JWK endpoint not reachable

Likely cause: backend cannot reach `docflow.security.oidc.jwk-set-uri` due to DNS, firewall, proxy, certificate, or wrong URL.

Fix: Verify network connectivity from the backend runtime environment to the IdP JWK set URI.

### CORS blocked by browser

Likely cause: frontend origin is not listed under `docflow.security.cors.allowed-origins`, or the request uses a header not listed under `allowed-headers`.

Fix: Add the exact frontend origin, such as `https://docflow-client.example.com`, and ensure `Authorization` is allowed.

### dev-login not accessible

Likely cause: `/api/auth/dev-login` is public only when `DEV_AUTH` is active and dev auth is allowed.

Fix: For local use, set `docflow.security.auth-mode=DEV_AUTH` and either run with `SPRING_PROFILES_ACTIVE=local` or set `docflow.security.allow-dev-auth=true`.

### User logs in but has only default maker access

Likely cause: authentication succeeded, but the user has no elevated internal DocFlow role mapping. `UserRoleService` may apply implicit `MAKER` access when enabled.

Fix: Add or correct the user's internal DocFlow role mapping. IdP groups are not used for DocFlow authorization.

### Elevated role not available

Likely cause: DocFlow internal role mapping or `ROLE_MODULE_ACCESS` configuration is missing, disabled, or does not include the required module.

Fix: Verify the user's internal role assignment and the corresponding role/module access entries.

