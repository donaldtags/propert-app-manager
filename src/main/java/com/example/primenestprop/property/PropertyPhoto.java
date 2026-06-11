package com.example.primenestprop.property;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "property_photos", indexes = {
        @Index(name = "idx_property_photos_property", columnList = "property_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PropertyPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Property property;

    @Column(length = 1000)
    private String photoUrl;

    @Column(length = 1000)
    private String storageKey;
    private int sortOrder;
    @Column(name = "is_primary")
    private boolean primaryPhoto;
    private Instant createdAt = Instant.now();
}
