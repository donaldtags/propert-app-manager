package com.example.primenestprop.chat;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository conversationParticipants;
    private final ChatMessageRepository messages;
    private final UserService users;
    private final PropertyService properties;

    public ChatService(
            ConversationRepository conversations,
            ConversationParticipantRepository conversationParticipants,
            ChatMessageRepository messages,
            UserService users,
            PropertyService properties
    ) {
        this.conversations = conversations;
        this.conversationParticipants = conversationParticipants;
        this.messages = messages;
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public Conversation start(ChatDtos.StartConversationRequest request, AppUser sender) {
        AppUser recipient = users.require(request.recipientId());
        if (recipient.getId().equals(sender.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot message yourself");
        }
        Conversation conversation = new Conversation();
        conversation.setParticipantOne(sender);
        conversation.setParticipantTwo(recipient);
        conversation.setCreatedBy(sender);
        conversation.setSubject(request.subject());
        if (request.propertyId() != null) {
            conversation.setProperty(properties.require(request.propertyId()));
        }
        conversations.save(conversation);
        conversationParticipants.save(new ConversationParticipant(conversation.getId(), sender.getId()));
        conversationParticipants.save(new ConversationParticipant(conversation.getId(), recipient.getId()));
        addMessage(conversation, sender, request.content(), request.messageType() == null ? MessageType.GENERAL : request.messageType());
        return conversation;
    }

    @Transactional
    public ChatMessage send(ChatDtos.SendMessageRequest request, AppUser sender) {
        Conversation conversation = requireParticipant(request.conversationId(), sender);
        return addMessage(conversation, sender, request.content(), MessageType.GENERAL);
    }

    @Transactional(readOnly = true)
    public List<Conversation> conversationsFor(Long userId, AppUser currentUser) {
        if (!userId.equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own conversations");
        }
        return conversations.findForUser(currentUser);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> thread(Long conversationId, AppUser currentUser) {
        Conversation conversation = requireParticipant(conversationId, currentUser);
        return messages.findByConversationOrderByCreatedAtAsc(conversation);
    }

    @Transactional
    public void markRead(Long conversationId, AppUser currentUser) {
        Conversation conversation = requireParticipant(conversationId, currentUser);
        for (ChatMessage message : messages.findByConversationAndSenderNotAndReadAtIsNull(conversation, currentUser)) {
            message.setReadAt(Instant.now());
        }
    }

    public long unreadCount(Conversation conversation, AppUser currentUser) {
        return messages.countByConversationAndSenderNotAndReadAtIsNull(conversation, currentUser);
    }

    private ChatMessage addMessage(Conversation conversation, AppUser sender, String content, MessageType messageType) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setMessageType(messageType);
        messages.save(message);
        conversation.setLastMessage(content);
        conversation.setLastMessageAt(message.getCreatedAt());
        return message;
    }

    private Conversation requireParticipant(Long conversationId, AppUser user) {
        Conversation conversation = conversations.findWithParticipantsById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getParticipantOne().getId().equals(user.getId())
                && !conversation.getParticipantTwo().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not part of this conversation");
        }
        return conversation;
    }
}
