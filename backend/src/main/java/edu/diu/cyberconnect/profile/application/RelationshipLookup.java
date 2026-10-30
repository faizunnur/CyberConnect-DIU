package edu.diu.cyberconnect.profile.application;

import java.util.Set;
import java.util.UUID;

/**
 * Port implemented by the networking module. Keeps the profile module free of a dependency on networking while
 * visibility rules still honour connections and blocks.
 */
public interface RelationshipLookup {

    Relationship between(UUID viewerId, UUID targetId);

    /** Members the user has blocked or who blocked the user. */
    Set<UUID> blockedEitherWay(UUID userId);

    Set<UUID> connectedWith(UUID userId);
}
