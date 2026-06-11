package com.example.primenestprop.user;

import java.io.Serializable;
import java.util.Objects;

public class UserRoleAssignmentId implements Serializable {
    private Long userId;
    private Long roleId;

    public UserRoleAssignmentId() {
    }

    public UserRoleAssignmentId(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRoleAssignmentId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
