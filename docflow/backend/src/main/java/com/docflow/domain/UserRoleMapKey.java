package com.docflow.domain;


import java.io.Serializable;

public  class UserRoleMapKey implements Serializable {
    private String userId;
    private String role;

    public UserRoleMapKey() {
    }

    public UserRoleMapKey(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleMapKey that = (UserRoleMapKey) o;
        return java.util.Objects.equals(userId, that.userId) && java.util.Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(userId, role);
    }
}

