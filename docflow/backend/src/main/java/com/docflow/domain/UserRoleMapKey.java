package com.docflow.domain;


import java.io.Serializable;

public class UserRoleMapKey implements Serializable {
    private String userId;
    private String roleName;

    public UserRoleMapKey() {
    }

    public UserRoleMapKey(String userId, String roleName) {
        this.userId = userId;
        this.roleName = roleName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleMapKey that = (UserRoleMapKey) o;
        return java.util.Objects.equals(userId, that.userId) && java.util.Objects.equals(roleName, that.roleName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(userId, roleName);
    }
}
