package com.example.primenestprop.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversation_participants")
@IdClass(ConversationParticipantId.class)
@Getter
@Setter
@NoArgsConstructor
public class ConversationParticipant {
    @Id
    @Column(name = "conversation_id")
    private Long conversationId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    public ConversationParticipant(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }
}
