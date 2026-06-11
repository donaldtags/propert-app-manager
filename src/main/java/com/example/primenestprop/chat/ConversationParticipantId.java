package com.example.primenestprop.chat;

import java.io.Serializable;
import java.util.Objects;

public class ConversationParticipantId implements Serializable {
    private Long conversationId;
    private Long userId;

    public ConversationParticipantId() {
    }

    public ConversationParticipantId(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConversationParticipantId that)) {
            return false;
        }
        return Objects.equals(conversationId, that.conversationId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, userId);
    }
}
