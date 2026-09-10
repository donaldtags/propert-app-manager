package com.example.primenestprop.construction;

import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "construction_updates", indexes = {
        @Index(name = "idx_construction_updates_project", columnList = "project_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ConstructionUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ConstructionProject project;

    @Column(length = 2000, nullable = false)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser postedBy;

    private Instant createdAt = Instant.now();
}
