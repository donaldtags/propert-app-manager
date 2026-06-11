package com.example.primenestprop.chat;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
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
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversations_participants", columnList = "participant_one_id,participant_two_id"),
        @Index(name = "idx_conversations_updated", columnList = "last_message_at"),
        @Index(name = "idx_conversations_created_by", columnList = "created_by_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser participantOne;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser participantTwo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser createdBy;

    private String subject;
    private String lastMessage;
    private Instant lastMessageAt = Instant.now();
    private Instant createdAt = Instant.now();
}
