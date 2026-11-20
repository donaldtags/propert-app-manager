package com.example.estatex.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByToken(String token);

    void deleteByToken(String token);

    /** Signs the user out everywhere. */
    @Modifying
    @Query("delete from AuthSession s where s.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    /** Signs the user out everywhere except the session making the request. */
    @Modifying
    @Query("delete from AuthSession s where s.user.id = :userId and s.token <> :keepToken")
    int deleteOthersForUser(@Param("userId") Long userId, @Param("keepToken") String keepToken);
}
