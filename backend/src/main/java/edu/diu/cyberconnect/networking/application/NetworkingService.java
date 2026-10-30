package edu.diu.cyberconnect.networking.application;

import edu.diu.cyberconnect.profile.application.MemberCard;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Public API of the networking module: connection requests, connections and blocks. */
public interface NetworkingService {

    /** Sends a request; a pending request in the other direction is accepted instead of duplicated. */
    ConnectionRequestView request(UUID requesterId, UUID addresseeId, String message);

    void accept(UUID userId, UUID requestId);

    void decline(UUID userId, UUID requestId);

    /** Requester withdraws a pending request. */
    void cancel(UUID userId, UUID requestId);

    /** Removes an accepted connection with the other member. */
    void remove(UUID userId, UUID otherId);

    Page<ConnectionView> connections(UUID userId, Pageable pageable);

    Page<ConnectionRequestView> incoming(UUID userId, Pageable pageable);

    Page<ConnectionRequestView> outgoing(UUID userId, Pageable pageable);

    NetworkSummary summary(UUID userId);

    /** Blocks the member; any connection or pending request between the two is removed. Idempotent. */
    void block(UUID userId, UUID targetId);

    void unblock(UUID userId, UUID targetId);

    List<MemberCard> blocked(UUID userId);

    boolean isConnected(UUID a, UUID b);

    boolean isBlockedEitherWay(UUID a, UUID b);
}
