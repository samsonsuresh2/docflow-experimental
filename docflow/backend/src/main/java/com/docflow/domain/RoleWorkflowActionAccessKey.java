package com.docflow.domain;

import java.io.Serializable;
import java.util.Objects;

public class RoleWorkflowActionAccessKey implements Serializable {

    private String roleName;
    private String fromStatus;
    private String actionCode;

    public RoleWorkflowActionAccessKey() {
    }

    public RoleWorkflowActionAccessKey(String roleName, String fromStatus, String actionCode) {
        this.roleName = roleName;
        this.fromStatus = fromStatus;
        this.actionCode = actionCode;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleWorkflowActionAccessKey)) return false;
        RoleWorkflowActionAccessKey that = (RoleWorkflowActionAccessKey) o;
        return Objects.equals(roleName, that.roleName)
            && Objects.equals(fromStatus, that.fromStatus)
            && Objects.equals(actionCode, that.actionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleName, fromStatus, actionCode);
    }
}
