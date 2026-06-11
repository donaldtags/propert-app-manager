package com.example.primenestprop.chat;

import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_conversation", columnList = "conversation_id,createdAt"),
        @Index(name = "idx_messages_sender", columnList = "sender_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Conversation conversation;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser sender;

    @Column(nullable = false, length = 4000)
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageType messageType = MessageType.GENERAL;

    private Instant readAt;
    private Instant createdAt = Instant.now();
}
