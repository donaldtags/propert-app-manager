package com.example.primenestprop.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleAssignmentId.class)
@Getter
@Setter
@NoArgsConstructor
public class UserRoleAssignment {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "role_id")
    private Long roleId;

    public UserRoleAssignment(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }
}
