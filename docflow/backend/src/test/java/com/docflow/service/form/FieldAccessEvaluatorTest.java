package com.docflow.service.form;

import com.docflow.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FieldAccessEvaluatorTest {

    @Test
    void hiddenWhenRoleNotAllowed() {
        UploadFieldDefinition definition = new UploadFieldDefinition();
        definition.setName("alpha");
        definition.setVisibleToRoles(Set.of("MAKER"));

        FieldAccessDecision decision = FieldAccessEvaluator.evaluate(definition, "REVIEWER", DocumentStatus.DRAFT, null, true);

        assertThat(decision.isVisible()).isFalse();
        assertThat(decision.isEditable()).isFalse();
    }

    @Test
    void nonEditableWhenRoleMissing() {
        UploadFieldDefinition definition = new UploadFieldDefinition();
        definition.setName("beta");
        definition.setEditableByRoles(Set.of("REVIEWER"));

        FieldAccessDecision decision = FieldAccessEvaluator.evaluate(definition, "MAKER", DocumentStatus.DRAFT, null, true);

        assertThat(decision.isEditable()).isFalse();
    }

    @Test
    void lockedWhenValueAlreadyPresent() {
        UploadFieldDefinition definition = new UploadFieldDefinition();
        definition.setName("gamma");
        definition.setLockAfterFilled(true);

        FieldAccessDecision decision = FieldAccessEvaluator.evaluate(definition, "MAKER", DocumentStatus.DRAFT, "existing", true);

        assertThat(decision.isLocked()).isTrue();
        assertThat(decision.isEditable()).isFalse();
    }

    @Test
    void requiredOnlyAtMatchingStatuses() {
        UploadFieldDefinition definition = new UploadFieldDefinition();
        definition.setName("delta");
        definition.setRequiredAtStatuses(Set.of("APPROVED"));

        FieldAccessDecision requiredDecision = FieldAccessEvaluator.evaluate(definition, "MAKER", DocumentStatus.APPROVED, "", true);
        FieldAccessDecision notRequiredDecision = FieldAccessEvaluator.evaluate(definition, "MAKER", DocumentStatus.DRAFT, "", true);

        assertThat(requiredDecision.isRequiredNow()).isTrue();
        assertThat(notRequiredDecision.isRequiredNow()).isFalse();
    }

    @Test
    void legacyFieldsStayVisibleEditableAndRequired() {
        UploadFieldDefinition definition = new UploadFieldDefinition();
        definition.setName("epsilon");
        definition.setRequired(true);

        FieldAccessDecision decision = FieldAccessEvaluator.evaluate(definition, "ANY", DocumentStatus.DRAFT, "", true);

        assertThat(decision.isVisible()).isTrue();
        assertThat(decision.isEditable()).isTrue();
        assertThat(decision.isRequiredNow()).isTrue();
    }

    @Test
    void visibleIfRespectsNotInValues() {
        UploadFieldDefinition definition = new UploadFieldDefinition();
        definition.setName("zeta");
        VisibleIfCondition condition = new VisibleIfCondition();
        condition.setField("other");
        condition.setNotIn(Set.of("block"));
        definition.setVisibleIf(condition);

        boolean hidden = FieldAccessEvaluator.evaluateVisibleIf(definition, Map.of("other", "block"));
        boolean shown = FieldAccessEvaluator.evaluateVisibleIf(definition, Map.of("other", "different"));

        assertThat(hidden).isFalse();
        assertThat(shown).isTrue();
    }
}
