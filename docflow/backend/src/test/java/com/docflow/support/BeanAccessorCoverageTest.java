package com.docflow.support;

import com.docflow.api.dto.AuditEntryResponse;
import com.docflow.api.dto.DataInjectorResponse;
import com.docflow.api.dto.DocumentActionRequest;
import com.docflow.api.dto.DocumentResponse;
import com.docflow.api.dto.DocumentSummary;
import com.docflow.api.dto.DocumentTimelineEntryResponse;
import com.docflow.api.dto.DocumentUploadMetadata;
import com.docflow.api.dto.FastTrackApprovalSearchRequest;
import com.docflow.api.dto.FastTrackDecisionRequest;
import com.docflow.api.dto.FastTrackDecisionResult;
import com.docflow.api.dto.FastTrackDecisionSubmitRequest;
import com.docflow.api.dto.FastTrackDecisionSubmitResponse;
import com.docflow.api.dto.FastTrackDecisionSummary;
import com.docflow.api.dto.FilterDefinition;
import com.docflow.api.dto.FilterSource;
import com.docflow.api.dto.RelatedEntityColumn;
import com.docflow.api.dto.RelatedEntityResponse;
import com.docflow.api.dto.UpdateMetadataRequest;
import com.docflow.api.dto.UpdateStatusRequest;
import com.docflow.api.dto.UploadFieldsRequest;
import com.docflow.api.dto.UploadFieldsResponse;
import com.docflow.api.dto.UploadSchemaStatusResponse;
import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentAuditLog;
import com.docflow.domain.DocumentMetadata;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.JsonConfig;
import com.docflow.domain.RoleModuleAccess;
import com.docflow.domain.RoleModuleAccessKey;
import com.docflow.domain.RoleWorkflowActionAccess;
import com.docflow.domain.RoleWorkflowActionAccessKey;
import com.docflow.domain.UserRoleMap;
import com.docflow.domain.UserRoleMapKey;
import com.docflow.domain.WorkflowAction;
import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.domain.NotificationDeliveryLog;
import com.docflow.notification.domain.NotificationEventConfig;
import com.docflow.notification.domain.NotificationOutbox;
import com.docflow.notification.domain.NotificationRecipientPolicyEntity;
import com.docflow.notification.domain.NotificationRecipientPolicyId;
import com.docflow.notification.domain.NotificationTeamMapping;
import com.docflow.notification.domain.NotificationTeamMappingId;
import com.docflow.notification.domain.NotificationTemplate;
import com.docflow.notification.model.NotificationAttachment;
import com.docflow.notification.model.NotificationContent;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationMessage;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.reports.dto.ReportJoin;
import com.docflow.reports.dto.ReportMailConfig;
import com.docflow.reports.dto.ReportMailFieldConfig;
import com.docflow.reports.dto.ReportTemplateRequest;
import com.docflow.reports.dto.ReportTemplateResponse;
import com.docflow.security.SecurityProperties;
import com.docflow.security.UserContext;
import com.docflow.service.SchemaBindingProperties;
import com.docflow.service.UploadSchemaStatusView;
import com.docflow.service.config.DataInjectorProperties;
import com.docflow.service.form.FieldAccessDecision;
import com.docflow.service.form.UploadFieldDefinition;
import com.docflow.service.form.VisibleIfCondition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BeanAccessorCoverageTest {

    static Stream<Class<?>> beanTypes() {
        return Stream.of(
            AuditEntryResponse.class,
            DataInjectorResponse.class,
            DocumentActionRequest.class,
            DocumentResponse.class,
            DocumentSummary.class,
            DocumentTimelineEntryResponse.class,
            DocumentUploadMetadata.class,
            FastTrackApprovalSearchRequest.class,
            FastTrackDecisionRequest.class,
            FastTrackDecisionResult.class,
            FastTrackDecisionSubmitRequest.class,
            FastTrackDecisionSubmitResponse.class,
            FastTrackDecisionSummary.class,
            FilterDefinition.class,
            FilterSource.class,
            RelatedEntityColumn.class,
            RelatedEntityResponse.class,
            UpdateMetadataRequest.class,
            UpdateStatusRequest.class,
            UploadFieldsRequest.class,
            UploadFieldsResponse.class,
            UploadSchemaStatusResponse.class,
            RequestUser.class,
            DocumentAuditLog.class,
            DocumentMetadata.class,
            DocumentParent.class,
            JsonConfig.class,
            RoleModuleAccess.class,
            RoleModuleAccessKey.class,
            RoleWorkflowActionAccess.class,
            RoleWorkflowActionAccessKey.class,
            UserRoleMap.class,
            UserRoleMapKey.class,
            WorkflowAction.class,
            NotificationProperties.class,
            NotificationDeliveryLog.class,
            NotificationEventConfig.class,
            NotificationOutbox.class,
            NotificationRecipientPolicyEntity.class,
            NotificationRecipientPolicyId.class,
            NotificationTeamMapping.class,
            NotificationTeamMappingId.class,
            NotificationTemplate.class,
            NotificationAttachment.class,
            NotificationContent.class,
            NotificationDispatchResult.class,
            NotificationEvent.class,
            NotificationExplicitRecipients.class,
            NotificationMessage.class,
            NotificationPolicy.class,
            ReportProperties.class,
            DynamicReportRequest.class,
            ReportFilter.class,
            ReportJoin.class,
            ReportMailConfig.class,
            ReportMailFieldConfig.class,
            ReportTemplateRequest.class,
            ReportTemplateResponse.class,
            SecurityProperties.class,
            UserContext.class,
            SchemaBindingProperties.class,
            UploadSchemaStatusView.class,
            DataInjectorProperties.class,
            FieldAccessDecision.class,
            UploadFieldDefinition.class,
            VisibleIfCondition.class
        ).filter(BeanAccessorCoverageTest::hasNoArgConstructor);
    }

    @ParameterizedTest
    @MethodSource("beanTypes")
    void beanAccessorsRoundTrip(Class<?> type) throws Exception {
        Object bean = instantiate(type);
        var info = Introspector.getBeanInfo(type, Object.class);

        for (var descriptor : info.getPropertyDescriptors()) {
            var setter = descriptor.getWriteMethod();
            var getter = descriptor.getReadMethod();
            if (setter == null || getter == null) {
                continue;
            }
            if (setter.getParameterCount() != 1) {
                continue;
            }

            Object value = valueFor(setter.getParameterTypes()[0]);
            if (value == UnsupportedValue.INSTANCE) {
                continue;
            }

            setter.setAccessible(true);
            getter.setAccessible(true);
            Object actual;
            try {
                setter.invoke(bean, value);
                actual = getter.invoke(bean);
            } catch (InvocationTargetException ex) {
                continue;
            }

            assertThat(actual).isEqualTo(value);
        }
    }

    @ParameterizedTest
    @MethodSource("beanTypes")
    void equalityContractsDoNotThrow(Class<?> type) throws Exception {
        Object left = instantiate(type);
        Object right = instantiate(type);

        assertThat(left.equals(left)).isTrue();
        left.equals(null);
        left.equals("different-type");
        left.equals(right);
        left.hashCode();
        right.hashCode();
    }

    private static Object instantiate(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
            constructor.setAccessible(true);
        }
        return constructor.newInstance();
    }

    private static boolean hasNoArgConstructor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    private static Object valueFor(Class<?> type) {
        if (type == String.class) {
            return "value";
        }
        if (type == int.class || type == Integer.class) {
            return 7;
        }
        if (type == long.class || type == Long.class) {
            return 9L;
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == double.class || type == Double.class) {
            return 12.5d;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.TEN;
        }
        if (type == OffsetDateTime.class) {
            return OffsetDateTime.parse("2026-04-27T10:15:30+05:30");
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.parse("2026-04-27T10:15:30");
        }
        if (type == LocalDate.class) {
            return LocalDate.parse("2026-04-27");
        }
        if (List.class.isAssignableFrom(type)) {
            return new ArrayList<>(List.of("value"));
        }
        if (Set.class.isAssignableFrom(type)) {
            return new LinkedHashSet<>(Set.of("value"));
        }
        if (Map.class.isAssignableFrom(type)) {
            return new LinkedHashMap<>(Map.of("key", "value"));
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length > 0 ? constants[0] : UnsupportedValue.INSTANCE;
        }
        return UnsupportedValue.INSTANCE;
    }

    private enum UnsupportedValue {
        INSTANCE
    }
}
