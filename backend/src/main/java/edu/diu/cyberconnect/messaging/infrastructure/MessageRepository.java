package edu.diu.cyberconnect.messaging.infrastructure;

import edu.diu.cyberconnect.messaging.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("select m from Message m where m.conversationId = :conversationId order by m.createdAt desc, m.id desc")
    List<Message> latest(@Param("conversationId") UUID conversationId, Pageable pageable);

    @Query("""
            select m from Message m where m.conversationId = :conversationId
              and (m.createdAt < :before or (m.createdAt = :before and m.id < :beforeId))
             order by m.createdAt desc, m.id desc""")
    List<Message> before(@Param("conversationId") UUID conversationId, @Param("before") Instant before,
            @Param("beforeId") UUID beforeId, Pageable pageable);

    long countByConversationIdAndSenderId(UUID conversationId, UUID senderId);

    @Query("""
            select count(m) from Message m where m.conversationId = :conversationId and m.senderId <> :userId
              and m.deletedAt is null""")
    long unreadAll(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("""
            select count(m) from Message m where m.conversationId = :conversationId and m.senderId <> :userId
              and m.deletedAt is null and m.createdAt > :since""")
    long unreadSince(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId, @Param("since") Instant since);

    /** PostgreSQL cannot type a nullable timestamp parameter, hence the two variants. */
    default long unread(UUID conversationId, UUID userId, Instant since) {
        return since == null ? unreadAll(conversationId, userId) : unreadSince(conversationId, userId, since);
    }
}
