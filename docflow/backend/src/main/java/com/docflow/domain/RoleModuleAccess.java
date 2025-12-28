package com.docflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "role_module_access")
@IdClass(RoleModuleAccessKey.class)
public class RoleModuleAccess {

    @jakarta.persistence.Id
    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @jakarta.persistence.Id
    @Column(name = "module_code", nullable = false, length = 100)
    private String moduleCode;

    @Column(name = "enabled")
    private String enabled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
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
        if (o == null || getClass() != o.getClass()) return false;
        RoleModuleAccess that = (RoleModuleAccess) o;
        return Objects.equals(role, that.role) && Objects.equals(moduleCode, that.moduleCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, moduleCode);
    }
}

public class RoleModuleAccessKey implements Serializable {
    private String role;
    private String moduleCode;

    public RoleModuleAccessKey() {
    }

    public RoleModuleAccessKey(String role, String moduleCode) {
        this.role = role;
        this.moduleCode = moduleCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleModuleAccessKey that = (RoleModuleAccessKey) o;
        return java.util.Objects.equals(role, that.role) && java.util.Objects.equals(moduleCode, that.moduleCode);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(role, moduleCode);
    }
}
