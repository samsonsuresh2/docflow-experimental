package com.docflow.notification.domain;

import com.docflow.domain.BooleanToYNConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;

@Entity
@Table(name = "notification_recipient_policy")
public class NotificationRecipientPolicyEntity {

    @EmbeddedId
    private NotificationRecipientPolicyId id;

    @Convert(converter = BooleanToYNConverter.class)
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_enabled", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private Boolean enabled = Boolean.TRUE;

    public NotificationRecipientPolicyId getId() {
        return id;
    }

    public void setId(NotificationRecipientPolicyId id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
