package com.docflow.notification.service;

import com.docflow.domain.AuditCategory;
import com.docflow.domain.DocumentAuditLog;
import com.docflow.domain.repository.DocumentAuditLogRepository;
import com.docflow.notification.domain.NotificationTeamMapping;
import com.docflow.notification.domain.NotificationTeamMappingId;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.model.NotificationRecipientType;
import com.docflow.notification.model.NotificationReferenceType;
import com.docflow.notification.repository.NotificationTeamMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.docflow.notification.model.NotificationRecipientType.MAKER;
import static com.docflow.notification.model.NotificationRecipientType.PAST_PARTICIPANTS;
import static com.docflow.notification.model.NotificationRecipientType.TEAM_DL;

class DocumentRecipientResolverTest {

    private NotificationEmailAddressResolver emailAddressResolver;
    private NotificationTeamMappingRepository teamMappingRepository;
    private DocumentAuditLogRepository auditLogRepository;
    private DocumentRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        emailAddressResolver = mock(NotificationEmailAddressResolver.class);
        teamMappingRepository = mock(NotificationTeamMappingRepository.class);
        auditLogRepository = mock(DocumentAuditLogRepository.class);
        resolver = new DocumentRecipientResolver(emailAddressResolver, teamMappingRepository, auditLogRepository);

        when(emailAddressResolver.resolveUserAddress("maker1")).thenReturn(Optional.of("maker1@company.internal"));
        when(emailAddressResolver.resolveUserAddress("actor1")).thenReturn(Optional.of("actor1@company.internal"));
        when(emailAddressResolver.resolveUserAddress("reviewer1")).thenReturn(Optional.of("reviewer1@company.internal"));
        when(emailAddressResolver.resolveUserAddress("approver1")).thenReturn(Optional.of("approver1@company.internal"));
        when(emailAddressResolver.resolveUserAddress("metadata-editor")).thenReturn(Optional.of("metadata-editor@company.internal"));
        when(teamMappingRepository.findByIdFieldNameIgnoreCaseAndIdFieldValueIgnoreCaseAndActiveTrue("department", "OPS"))
                .thenReturn(Optional.of(teamMapping()));
    }

    @Test
    void submittedForReviewResolvesMakerAndTeamDlOnly() {
        NotificationExplicitRecipients resolved = resolve(baseEvent(), policy(MAKER, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void resubmittedResolvesMakerPastParticipantsAndTeamDl() {
        NotificationEvent event = baseEvent();
        OffsetDateTime actionOccurredAt = OffsetDateTime.parse("2026-04-13T10:00:00Z");
        event.setContext(Map.of(
                "makerUserId", "maker1",
                "actorUserId", "actor1",
                "routingFieldName", "department",
                "routingFieldValue", "OPS",
                "actionOccurredAt", actionOccurredAt.toString()
        ));

        when(auditLogRepository.findByDocument_IdAndAuditCategoryAndChangedAtBeforeOrderByChangedAtAsc(
                42L, AuditCategory.LIFECYCLE, actionOccurredAt
        )).thenReturn(List.of(
                lifecycleAudit("maker1", actionOccurredAt.minusMinutes(30)),
                lifecycleAudit("reviewer1", actionOccurredAt.minusMinutes(20)),
                lifecycleAudit("reviewer1", actionOccurredAt.minusMinutes(10))
        ));

        NotificationExplicitRecipients resolved = resolve(event, policy(MAKER, PAST_PARTICIPANTS, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "reviewer1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void reviewStartedResolvesMakerAndTeamDlOnly() {
        NotificationExplicitRecipients resolved = resolve(baseEvent(), policy(MAKER, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void sentBackToMakerResolvesMakerPastParticipantsAndTeamDl() {
        NotificationExplicitRecipients resolved = resolve(withLifecycleParticipants("reviewer1"), policy(MAKER, PAST_PARTICIPANTS, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "reviewer1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void reviewApprovedResolvesMakerPastParticipantsAndTeamDl() {
        NotificationExplicitRecipients resolved = resolve(withLifecycleParticipants("reviewer1"), policy(MAKER, PAST_PARTICIPANTS, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "reviewer1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void approvedResolvesMakerPastParticipantsAndTeamDl() {
        NotificationExplicitRecipients resolved = resolve(withLifecycleParticipants("reviewer1", "approver1"), policy(MAKER, PAST_PARTICIPANTS, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "reviewer1@company.internal",
                "approver1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void rejectedResolvesMakerPastParticipantsAndTeamDl() {
        NotificationExplicitRecipients resolved = resolve(withLifecycleParticipants("reviewer1"), policy(MAKER, PAST_PARTICIPANTS, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "reviewer1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void pastParticipantsExcludesNonLifecycleAuditActorsByUsingLifecycleAuditOnly() {
        NotificationEvent event = withLifecycleParticipants("reviewer1");

        resolve(event, policy(PAST_PARTICIPANTS));

        verify(auditLogRepository).findByDocument_IdAndAuditCategoryAndChangedAtBeforeOrderByChangedAtAsc(
                42L,
                AuditCategory.LIFECYCLE,
                OffsetDateTime.parse("2026-04-13T10:00:00Z")
        );
        verify(auditLogRepository, never()).findByDocument_IdOrderByChangedAtAsc(42L);
    }

    @Test
    void pastParticipantsExcludesCurrentActionFromLifecycleHistory() {
        OffsetDateTime latest = OffsetDateTime.parse("2026-04-13T10:00:00Z");
        when(auditLogRepository.findByDocument_IdAndAuditCategoryOrderByChangedAtAsc(42L, AuditCategory.LIFECYCLE))
                .thenReturn(List.of(
                        lifecycleAudit("maker1", latest.minusMinutes(20)),
                        lifecycleAudit("reviewer1", latest.minusMinutes(10)),
                        lifecycleAudit("actor1", latest),
                        lifecycleAudit("actor1", latest)
                ));

        NotificationEvent event = baseEvent();
        event.setContext(Map.of(
                "makerUserId", "maker1",
                "actorUserId", "actor1",
                "routingFieldName", "department",
                "routingFieldValue", "OPS"
        ));

        NotificationExplicitRecipients resolved = resolve(event, policy(PAST_PARTICIPANTS));

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "reviewer1@company.internal"
        );
    }

    @Test
    void teamDlFlowStillWorks() {
        NotificationExplicitRecipients resolved = resolve(baseEvent(), policy(TEAM_DL));

        assertThat(resolved.getTo()).containsExactly("ops-team@company.internal");
    }

    @Test
    void ignoresMissingTeamMappingWithoutFailing() {
        when(teamMappingRepository.findByIdFieldNameIgnoreCaseAndIdFieldValueIgnoreCaseAndActiveTrue("department", "OPS"))
                .thenReturn(Optional.empty());

        NotificationExplicitRecipients resolved = resolve(baseEvent(), policy(MAKER, TEAM_DL));

        assertThat(resolved.getTo()).containsExactly("maker1@company.internal");
    }

    private NotificationExplicitRecipients resolve(NotificationEvent event, NotificationPolicy policy) {
        return resolver.resolve(event, policy);
    }

    private NotificationEvent baseEvent() {
        NotificationEvent event = new NotificationEvent();
        event.setReferenceType(NotificationReferenceType.DOCUMENT);
        event.setReferenceId("42");
        event.setContext(Map.of(
                "makerUserId", "maker1",
                "actorUserId", "actor1",
                "routingFieldName", "department",
                "routingFieldValue", "OPS",
                "actionOccurredAt", "2026-04-13T10:00:00Z"
        ));
        return event;
    }

    private NotificationEvent withLifecycleParticipants(String... priorActors) {
        NotificationEvent event = baseEvent();
        OffsetDateTime actionOccurredAt = OffsetDateTime.parse("2026-04-13T10:00:00Z");
        List<DocumentAuditLog> auditEntries = new java.util.ArrayList<>();
        auditEntries.add(lifecycleAudit("maker1", actionOccurredAt.minusMinutes(30)));
        int offset = 20;
        for (String actor : priorActors) {
            auditEntries.add(lifecycleAudit(actor, actionOccurredAt.minusMinutes(offset)));
            offset -= 5;
        }
        when(auditLogRepository.findByDocument_IdAndAuditCategoryAndChangedAtBeforeOrderByChangedAtAsc(
                42L, AuditCategory.LIFECYCLE, actionOccurredAt
        )).thenReturn(auditEntries);
        return event;
    }

    private NotificationPolicy policy(NotificationRecipientType... types) {
        NotificationPolicy policy = new NotificationPolicy();
        EnumMap<NotificationRecipientType, Boolean> recipientPolicies = new EnumMap<>(NotificationRecipientType.class);
        for (NotificationRecipientType type : types) {
            recipientPolicies.put(type, true);
        }
        policy.setRecipientPolicies(recipientPolicies);
        return policy;
    }

    private NotificationTeamMapping teamMapping() {
        NotificationTeamMapping mapping = new NotificationTeamMapping();
        NotificationTeamMappingId id = new NotificationTeamMappingId();
        id.setFieldName("department");
        id.setFieldValue("OPS");
        mapping.setId(id);
        mapping.setTeamEmailDl("ops-team@company.internal");
        return mapping;
    }

    private DocumentAuditLog lifecycleAudit(String changedBy, OffsetDateTime changedAt) {
        DocumentAuditLog log = new DocumentAuditLog();
        log.setAuditCategory(AuditCategory.LIFECYCLE);
        log.setChangedBy(changedBy);
        log.setChangedAt(changedAt);
        return log;
    }
}
