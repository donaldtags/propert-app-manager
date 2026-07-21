package com.example.primenestprop.escrow;

import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "escrow_release_approvals", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"escrow_id", "approver_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class EscrowReleaseApproval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private EscrowTransaction escrow;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser approver;

    private Instant approvedAt = Instant.now();

    public EscrowReleaseApproval(EscrowTransaction escrow, AppUser approver) {
        this.escrow = escrow;
        this.approver = approver;
    }
}