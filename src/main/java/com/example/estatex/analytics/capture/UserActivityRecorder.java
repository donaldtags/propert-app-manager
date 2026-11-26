package com.example.estatex.analytics.capture;

import com.example.estatex.common.BusinessTime;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Notes that a signed-in user was active today. Called on every authenticated request, but only
 * the first request per user per day reaches the database (in-memory cache; the unique key
 * catches the rest, e.g. after a restart or on a second server). Never fails the request.
 */
@Component
public class UserActivityRecorder {
    private static final Logger log = LoggerFactory.getLogger(UserActivityRecorder.class);
    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(26)).maximumSize(200_000).build();

    public UserActivityRecorder(JdbcTemplate jdbc, BusinessTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public void touch(Long userId) {
        if (userId == null) return;
        LocalDate today = time.today();
        if (seen.asMap().putIfAbsent(userId + "@" + today, Boolean.TRUE) != null) return;
        try {
            Integer exists = jdbc.queryForObject("select count(*) from user_activity_days where activity_date = ? and user_id = ?",
                    Integer.class, Date.valueOf(today), userId);
            if (exists == null || exists == 0) {
                jdbc.update("insert into user_activity_days (user_id, activity_date) values (?, ?)", userId, Date.valueOf(today));
            }
        } catch (DataIntegrityViolationException raced) {
            // another request or server recorded it first
        } catch (RuntimeException e) {
            log.debug("Could not record activity for user {}: {}", userId, e.getMessage());
        }
    }
}
