package com.docflow.notification.service;

import com.docflow.notification.domain.NotificationTeamMapping;
import com.docflow.notification.domain.NotificationTeamMappingId;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.model.NotificationRecipientType;
import com.docflow.notification.model.NotificationReferenceType;
import com.docflow.notification.repository.NotificationTeamMappingRepository;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentRecipientResolverTest {

    @Test
    void resolvesMakerReviewerApproverActorAndTeamDl() {
        NotificationEmailAddressResolver emailAddressResolver = mock(NotificationEmailAddressResolver.class);
        NotificationTeamMappingRepository teamMappingRepository = mock(NotificationTeamMappingRepository.class);
        DocumentRecipientResolver resolver = new DocumentRecipientResolver(emailAddressResolver, teamMappingRepository);

        when(emailAddressResolver.resolveUserAddress("maker1")).thenReturn(Optional.of("maker1@company.internal"));
        when(emailAddressResolver.resolveUserAddress("reviewer1")).thenReturn(Optional.of("reviewer1@company.internal"));
        when(emailAddressResolver.resolveUserAddress("approver1")).thenReturn(Optional.of("approver1@company.internal"));
        when(emailAddressResolver.resolveUserAddress("actor1")).thenReturn(Optional.of("actor1@company.internal"));

        NotificationTeamMapping mapping = new NotificationTeamMapping();
        NotificationTeamMappingId id = new NotificationTeamMappingId();
        id.setFieldName("department");
        id.setFieldValue("OPS");
        mapping.setId(id);
        mapping.setTeamEmailDl("ops-team@company.internal");
        when(teamMappingRepository.findByIdFieldNameIgnoreCaseAndIdFieldValueIgnoreCaseAndActiveTrue("department", "OPS"))
                .thenReturn(Optional.of(mapping));

        NotificationEvent event = new NotificationEvent();
        event.setReferenceType(NotificationReferenceType.DOCUMENT);
        event.setContext(Map.of(
                "makerUserId", "maker1",
                "reviewerUserId", "reviewer1",
                "approverUserId", "approver1",
                "actorUserId", "actor1",
                "routingFieldName", "department",
                "routingFieldValue", "OPS"
        ));

        NotificationPolicy policy = new NotificationPolicy();
        EnumMap<NotificationRecipientType, Boolean> recipients = new EnumMap<>(NotificationRecipientType.class);
        recipients.put(NotificationRecipientType.MAKER, true);
        recipients.put(NotificationRecipientType.CURRENT_REVIEWER, true);
        recipients.put(NotificationRecipientType.CURRENT_APPROVER, true);
        recipients.put(NotificationRecipientType.CURRENT_ACTOR, true);
        recipients.put(NotificationRecipientType.TEAM_DL, true);
        policy.setRecipientPolicies(recipients);

        NotificationExplicitRecipients resolved = resolver.resolve(event, policy);

        assertThat(resolved.getTo()).containsExactly(
                "maker1@company.internal",
                "reviewer1@company.internal",
                "approver1@company.internal",
                "actor1@company.internal",
                "ops-team@company.internal"
        );
    }

    @Test
    void ignoresMissingTeamMappingWithoutFailing() {
        NotificationEmailAddressResolver emailAddressResolver = mock(NotificationEmailAddressResolver.class);
        NotificationTeamMappingRepository teamMappingRepository = mock(NotificationTeamMappingRepository.class);
        DocumentRecipientResolver resolver = new DocumentRecipientResolver(emailAddressResolver, teamMappingRepository);

        when(emailAddressResolver.resolveUserAddress("maker1")).thenReturn(Optional.of("maker1@company.internal"));
        when(teamMappingRepository.findByIdFieldNameIgnoreCaseAndIdFieldValueIgnoreCaseAndActiveTrue("department", "OPS"))
                .thenReturn(Optional.empty());

        NotificationEvent event = new NotificationEvent();
        event.setReferenceType(NotificationReferenceType.DOCUMENT);
        event.setContext(Map.of(
                "makerUserId", "maker1",
                "routingFieldName", "department",
                "routingFieldValue", "OPS"
        ));

        NotificationPolicy policy = new NotificationPolicy();
        EnumMap<NotificationRecipientType, Boolean> recipients = new EnumMap<>(NotificationRecipientType.class);
        recipients.put(NotificationRecipientType.MAKER, true);
        recipients.put(NotificationRecipientType.TEAM_DL, true);
        policy.setRecipientPolicies(recipients);

        NotificationExplicitRecipients resolved = resolver.resolve(event, policy);

        assertThat(resolved.getTo()).containsExactly("maker1@company.internal");
    }
}
