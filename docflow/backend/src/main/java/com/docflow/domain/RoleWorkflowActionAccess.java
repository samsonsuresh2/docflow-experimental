package com.docflow.domain;

import com.docflow.domain.BooleanToYNConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "role_workflow_action_access")
@IdClass(RoleWorkflowActionAccessKey.class)
public class RoleWorkflowActionAccess {

    @Id
    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Id
    @Column(name = "from_status", nullable = false, length = 50)
    private String fromStatus;

    @Id
    @Column(name = "action_code", nullable = false, length = 50)
    private String actionCode;

    @Column(name = "enabled", nullable = false, length = 1)
    @JdbcTypeCode(Types.CHAR)
    @Convert(converter = BooleanToYNConverter.class)
    private Boolean enabled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleWorkflowActionAccess)) return false;
        RoleWorkflowActionAccess that = (RoleWorkflowActionAccess) o;
        return Objects.equals(roleName, that.roleName)
            && Objects.equals(fromStatus, that.fromStatus)
            && Objects.equals(actionCode, that.actionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleName, fromStatus, actionCode);
    }
}
