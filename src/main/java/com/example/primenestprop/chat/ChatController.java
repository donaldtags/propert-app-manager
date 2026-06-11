package com.example.primenestprop.chat;

import com.example.primenestprop.auth.AuthService;
import com.example.primenestprop.chat.ChatDtos.ConversationResponse;
import com.example.primenestprop.chat.ChatDtos.MessageResponse;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class ChatController {
    private final ChatService service;
    private final AuthService authService;

    public ChatController(ChatService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/conversations")
    List<ConversationResponse> conversations(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam Long userId
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return service.conversationsFor(userId, currentUser).stream()
                .map(conversation -> ConversationResponse.from(conversation, service.unreadCount(conversation, currentUser)))
                .toList();
    }

    @PostMapping("/conversations")
    ConversationResponse start(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChatDtos.StartConversationRequest request
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        Conversation conversation = service.start(request, currentUser);
        return ConversationResponse.from(conversation, service.unreadCount(conversation, currentUser));
    }

    @GetMapping("/conversations/{conversationId}")
    List<MessageResponse> thread(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long conversationId
    ) {
        AppUser currentUser = authService.currentUser(authorization);
        return service.thread(conversationId, currentUser).stream().map(MessageResponse::from).toList();
    }

    @PatchMapping("/conversations/{conversationId}/read")
    Map<String, Object> markRead(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long conversationId
    ) {
        service.markRead(conversationId, authService.currentUser(authorization));
        return Map.of("read", true);
    }

    @PostMapping
    MessageResponse send(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChatDtos.SendMessageRequest request
    ) {
        return MessageResponse.from(service.send(request, authService.currentUser(authorization)));
    }
}
