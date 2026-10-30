package edu.diu.cyberconnect.moderation.infrastructure;

import edu.diu.cyberconnect.moderation.domain.ModerationAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    List<ModerationAction> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId);

    /** Suspensions whose expiry has passed and that have not been followed by a lift yet. */
    List<ModerationAction> findByActionAndExpiresAtBefore(ModerationAction.Type action, Instant before);
}
