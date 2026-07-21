package com.example.primenestprop.chat;

import com.example.primenestprop.user.AppUser;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes a user's real average reply time from their actual message history: for every
 * incoming message (sent by the other participant), find the next message this user sent in
 * that same conversation afterward, and average the gaps. Replies that took longer than 7 days
 * are excluded from the average (almost certainly an abandoned thread, not a "response"), but are
 * still counted against the response rate below.
 */
@Service
public class ResponseRateService {
    private static final Duration STALE_THRESHOLD = Duration.ofDays(7);

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;

    public ResponseRateService(ConversationRepository conversations, ChatMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    public record Result(Double averageResponseHours, Integer responseRatePercent, int incomingMessageCount) {
        static Result empty() {
            return new Result(null, null, 0);
        }
    }

    @Transactional(readOnly = true)
    public Result compute(AppUser user) {
        List<Conversation> userConversations = conversations.findForUser(user);
        if (userConversations.isEmpty()) {
            return Result.empty();
        }

        List<ChatMessage> all = messages.findByConversationInOrderByCreatedAtAsc(userConversations);
        if (all.isEmpty()) {
            return Result.empty();
        }

        List<Double> replyHoursWithinThreshold = new java.util.ArrayList<>();
        int incomingCount = 0;
        int repliedCount = 0;

        var byConversation = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(ChatMessage::getConversation));

        for (List<ChatMessage> thread : byConversation.values()) {
            thread.sort(Comparator.comparing(ChatMessage::getCreatedAt));
            for (int i = 0; i < thread.size(); i++) {
                ChatMessage msg = thread.get(i);
                if (msg.getSender().getId().equals(user.getId())) {
                    continue;
                }
                // This is an incoming message. Find the next message this user sent afterward.
                incomingCount++;
                for (int j = i + 1; j < thread.size(); j++) {
                    ChatMessage candidate = thread.get(j);
                    if (candidate.getSender().getId().equals(user.getId())) {
                        Duration gap = Duration.between(msg.getCreatedAt(), candidate.getCreatedAt());
                        repliedCount++;
                        if (gap.compareTo(STALE_THRESHOLD) <= 0) {
                            replyHoursWithinThreshold.add(gap.toMinutes() / 60.0);
                        }
                        break;
                    }
                }
            }
        }

        if (incomingCount == 0) {
            return Result.empty();
        }

        OptionalDouble average = replyHoursWithinThreshold.stream().mapToDouble(Double::doubleValue).average();
        Double averageHours = average.isPresent() ? Math.round(average.getAsDouble() * 10) / 10.0 : null;
        int responseRatePercent = (int) Math.round((repliedCount * 100.0) / incomingCount);

        return new Result(averageHours, responseRatePercent, incomingCount);
    }
}
