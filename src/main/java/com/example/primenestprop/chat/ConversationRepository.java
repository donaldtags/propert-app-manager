package com.example.primenestprop.chat;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @EntityGraph(attributePaths = {"participantOne", "participantTwo", "property"})
    @Query("""
            select c from Conversation c
            where c.participantOne = :user or c.participantTwo = :user
            order by c.lastMessageAt desc
            """)
    List<Conversation> findForUser(@Param("user") AppUser user);

    @EntityGraph(attributePaths = {"participantOne", "participantTwo", "property"})
    java.util.Optional<Conversation> findWithParticipantsById(Long id);
}
