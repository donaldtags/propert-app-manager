package com.example.primenestprop.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record StartConversationRequest(
            @NotNull Long recipientId,
            @NotBlank String subject,
            @NotBlank String content,
            MessageType messageType,
            Long propertyId
    ) {
    }

    public record SendMessageRequest(@NotNull Long conversationId, @NotBlank String content) {
    }

    public record ConversationResponse(
            Long id,
            List<Long> participantIds,
            List<String> participantNames,
            Long propertyId,
            String subject,
            String lastMessage,
            Instant lastMessageAt,
            long unreadCount
    ) {
        public static ConversationResponse from(Conversation conversation, long unreadCount) {
            return new ConversationResponse(
                    conversation.getId(),
                    List.of(conversation.getParticipantOne().getId(), conversation.getParticipantTwo().getId()),
                    List.of(conversation.getParticipantOne().getFullName(), conversation.getParticipantTwo().getFullName()),
                    conversation.getProperty() == null ? null : conversation.getProperty().getId(),
                    conversation.getSubject(),
                    conversation.getLastMessage(),
                    conversation.getLastMessageAt(),
                    unreadCount
            );
        }
    }

    public record MessageResponse(
            Long id,
            Long conversationId,
            Long senderId,
            String senderName,
            String content,
            MessageType messageType,
            Instant readAt,
            Instant createdAt
    ) {
        public static MessageResponse from(ChatMessage message) {
            return new MessageResponse(
                    message.getId(),
                    message.getConversation().getId(),
                    message.getSender().getId(),
                    message.getSender().getFullName(),
                    message.getContent(),
                    message.getMessageType(),
                    message.getReadAt(),
                    message.getCreatedAt()
            );
        }
    }
}
