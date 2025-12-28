package com.docflow.domain;


import java.io.Serializable;

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
