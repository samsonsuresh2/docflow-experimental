DocFlow Notification Module (Phase 1) â€” Design README

---

## 1. Overview

The Notification Module in DocFlow enables **event-driven communication** to users and teams across document lifecycle and report workflows.

Phase 1 focuses on:

- **Email-based notifications only**
- **Document lifecycle event notifications**
- **Report sharing via email**
- **Config-driven recipient resolution**
- **Platform-neutral deployment (Linux VM / On-Prem / Cloud)**

The system is designed as a **generic notification framework**, with email as the first pluggable channel.

---

## 2. Design Principles

- **Event-driven architecture (not direct email calls)**
- **Channel abstraction (future-ready)**
- **Config-driven recipient control**
- **No impact on business transaction on mail failure**
- **Platform-agnostic (no cloud dependency)**
- **Minimal infra assumptions (only SMTP relay required)**

---

## 3. High-Level Flow

Business Action (Document / Report)
        â†“
Notification Event Published
        â†“
Notification Orchestrator
        â†“
Recipient Resolver + Policy Engine
        â†“
Template Renderer
        â†“
Channel Adapter (Email)
        â†“
SMTP Relay (Client Infra)
        â†“
Recipients

---

## 4. Supported Use Cases (Phase 1)

### 4.1 Document Lifecycle Notifications

Trigger notifications on:

- Document submission / open
- Review completed
- Approval completed
- Rejection / send back
- Bulk actions (config-controlled)

Recipients:

- Maker
- Reviewer(s)
- Approver(s)
- Team DL (derived from metadata)
- Impacted users across lifecycle

---

### 4.2 Report Email Sharing

From Reports UI:

- User clicks email icon
- Popup opens with:
  - Report name
  - Filter summary
- User enters:
  - To
  - CC (max 2)
  - Subject
  - Message
- Email is sent via notification service

---

## 5. Core Components

### 5.1 NotificationEventPublisher
- Triggered from business modules
- Emits events such as:
  - DOCUMENT_SUBMITTED
  - DOCUMENT_REVIEWED
  - DOCUMENT_APPROVED
  - DOCUMENT_REJECTED
  - DOCUMENT_BULK_APPROVED
  - REPORT_SHARED_EMAIL

---

### 5.2 NotificationOrchestrator
Central engine that:

- Identifies template
- Resolves recipients
- Applies policies
- Selects channel
- Triggers delivery
- Logs outcome

---

### 5.3 RecipientResolver

Builds recipient list from:

- Document creator
- Current actor
- Workflow participants
- Team DL (via metadata mapping)
- Explicit report recipients

---

### 5.4 TeamResolver

Resolves team DL using:

- Metadata JSON field (admin-configured)
- Backend mapping table

Example:

metadata.businessUnit = LOANS
â†’ lookup â†’ loans_team@company.com

---

### 5.5 NotificationPolicyEngine

Controls:

- Event enable/disable
- Recipient suppression (maker/reviewer/approver)
- Bulk action behavior
- Deduplication
- Actor inclusion/exclusion

---

### 5.6 TemplateRenderer

- Builds subject + body
- Supports placeholders (e.g., documentId, status, user)
- Supports HTML emails

---

### 5.7 NotificationChannel (Interface)

Phase 1 implementation:

- EmailNotificationChannel

Future:

- In-App
- SMS
- Teams/Slack
- Webhooks

---

### 5.8 Outbox Processor (Recommended)

- Stores events in DB
- Async worker sends emails
- Supports retry and resilience

---

## 6. Data Model

### 6.1 notification_event_config

| Column | Description |
|------|-------------|
| event_code | Unique event |
| module_name | Document / Report |
| is_enabled | Toggle |
| channel_type | EMAIL |
| template_code | Template reference |
| send_for_bulk | Boolean |

---

### 6.2 notification_recipient_policy

| Column | Description |
|------|-------------|
| event_code | Event |
| recipient_type | MAKER / REVIEWER / APPROVER / TEAM_DL |
| is_enabled | Toggle |

---

### 6.3 notification_team_mapping

| Column | Description |
|------|-------------|
| field_name | JSON field |
| field_value | e.g., LOANS |
| team_name | Logical name |
| team_email_dl | Email DL |

---

### 6.4 notification_template

| Column | Description |
|------|-------------|
| template_code | Identifier |
| subject_template | Subject |
| body_template | HTML/Text |
| is_active | Flag |

---

### 6.5 notification_delivery_log

| Column | Description |
|------|-------------|
| id | PK |
| event_code | Event |
| reference_id | Document/Report ID |
| recipient_email | Email |
| status | SUCCESS/FAILED |
| error | Failure reason |
| timestamp | Time |

---

### 6.6 notification_outbox (Optional but Recommended)

| Column | Description |
|------|-------------|
| id | PK |
| event_payload | JSON |
| status | PENDING/SENT/FAILED |
| retry_count | Retry logic |

---

## 7. Recipient Types

- MAKER
- CURRENT_REVIEWER
- CURRENT_APPROVER
- ALL_REVIEWERS
- ALL_APPROVERS
- CURRENT_ACTOR
- TEAM_DL
- EXPLICIT_TO
- EXPLICIT_CC

---

## 8. Key Behavior Rules

### 8.1 Deduplication
Same email should receive only one message per event.

---

### 8.2 Actor Control
Actor may or may not receive mail based on config.

---

### 8.3 Bulk Control
Bulk actions must have separate event handling.

---

### 8.4 Failure Isolation
Email failure must NOT fail business transaction.

---

### 8.5 Missing Mapping
If team mapping missing:
- Continue flow
- Log warning

---

## 9. Infra Requirements (Phase 1)

### Mandatory

- SMTP relay access

### Required Details

- SMTP host
- Port
- TLS / STARTTLS
- Auth (if required)
- Username/password (if applicable)
- Allowed sender email
- Network connectivity

---

### Optional (Enterprise)

- Rate limits
- Attachment size limits
- Non-prod email restrictions
- External email restrictions

---

## 10. Supported Deployment Modes

### Mode 1 (Preferred)
App â†’ SMTP Relay

---

### Mode 2
App â†’ Local Relay (Postfix) â†’ SMTP

---

### Mode 3 (Fallback)
App â†’ OS mail (mailx) â†’ Relay

---

## 11. Application Configuration

mail.enabled=true
mail.host=smtp.company.com
mail.port=587
mail.username=xxx
mail.password=xxx
mail.smtp.auth=true
mail.smtp.starttls.enable=true
mail.fromAddress=noreply@company.com
mail.replyTo=support@company.com
mail.connectionTimeout=5000
mail.readTimeout=5000
mail.writeTimeout=5000

---

## 12. Report Email API Contract

SendReportEmailRequest
- reportId
- reportName
- filterSummary
- toRecipients
- ccRecipients
- subject
- message

---

## 13. Future Extensions

### Phase 2 / 3

- In-app notification (bell icon)
- Notification history per user
- Read/unread tracking
- Notification preferences
- Multi-channel delivery
- Kafka/event-bus integration

---

## 14. Module Structure

notification-core
notification-email
notification-template
notification-outbox
notification-admin-config
notification-api

---

## 15. Final Design Principle

> â€œNotification is not sending an email.  
> It is reacting to a business event and delivering it through a configurable channel.â€

---

## 16. Summary

Phase 1 delivers:

- Email notifications for document lifecycle
- Report sharing via email
- Config-driven recipient and policy control
- Infra-light SMTP-based integration
- Future-ready extensible architecture

---

## 17. Status

- Phase: Design Finalized
- Ready for: Implementation
- Dependencies: SMTP relay availability