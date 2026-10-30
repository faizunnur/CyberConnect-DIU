package edu.diu.cyberconnect.messaging.infrastructure;

import edu.diu.cyberconnect.messaging.domain.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByDirectKey(String directKey);
}
