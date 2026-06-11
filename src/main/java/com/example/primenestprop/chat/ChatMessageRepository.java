package com.example.primenestprop.chat;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @EntityGraph(attributePaths = {"conversation", "sender"})
    List<ChatMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    long countByConversationAndSenderNotAndReadAtIsNull(Conversation conversation, AppUser sender);

    List<ChatMessage> findByConversationAndSenderNotAndReadAtIsNull(Conversation conversation, AppUser sender);
}
