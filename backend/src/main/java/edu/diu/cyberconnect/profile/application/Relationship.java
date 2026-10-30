package edu.diu.cyberconnect.profile.application;

import java.util.UUID;

/** Raw relationship facts supplied by the networking module (see {@link RelationshipLookup}). */
public record Relationship(boolean connected, boolean blockedByViewer, boolean blockedViewer,
        boolean pendingIncoming, boolean pendingOutgoing, UUID requestId) {

    public static final Relationship NONE = new Relationship(false, false, false, false, false, null);

    public boolean blockedEitherWay() {
        return blockedByViewer || blockedViewer;
    }
}
