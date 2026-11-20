package com.example.estatex.auth.sms;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development/test stand-in when no SMS provider is configured: writes each message to the log
 * and keeps the last few in memory so tests can read the code that was "sent".
 */
public class LoggingSmsGateway implements SmsGateway {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);
    private static final int KEEP = 200;
    private static final Deque<Sent> OUTBOX = new ArrayDeque<>();

    public record Sent(String to, String message) {}

    @Override
    public void send(String toE164, String message) {
        log.info("[SMS not sent - no provider configured] to {}: {}", toE164, message);
        synchronized (OUTBOX) {
            OUTBOX.addLast(new Sent(toE164, message));
            while (OUTBOX.size() > KEEP) OUTBOX.removeFirst();
        }
    }

    /** The most recent message "sent" to this number (tests and local development). */
    public static Optional<String> lastMessageTo(String toE164) {
        synchronized (OUTBOX) {
            var it = OUTBOX.descendingIterator();
            while (it.hasNext()) {
                Sent s = it.next();
                if (s.to().equals(toE164)) return Optional.of(s.message());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean deliversRealMessages() {
        return false;
    }

    @Override
    public String name() {
        return "log";
    }
}
