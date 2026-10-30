package edu.diu.cyberconnect.messaging.infrastructure;

import edu.diu.cyberconnect.common.model.ConversationState;
import edu.diu.cyberconnect.messaging.domain.ConversationMember;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMember.Key> {

    List<ConversationMember> findByIdConversationId(UUID conversationId);

    @Query(value = """
            select m from ConversationMember m, Conversation c
             where c.id = m.id.conversationId and m.id.userId = :userId and m.state in :states
             order by c.lastMessageAt desc nulls last, c.createdAt desc""",
            countQuery = """
            select count(m) from ConversationMember m
             where m.id.userId = :userId and m.state in :states""")
    Page<ConversationMember> inbox(@Param("userId") UUID userId, @Param("states") Collection<ConversationState> states,
            Pageable pageable);

    long countByIdUserIdAndState(UUID userId, ConversationState state);
}
