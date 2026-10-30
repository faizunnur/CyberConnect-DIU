package edu.diu.cyberconnect.profile.application;

import java.util.UUID;

/** How the viewer relates to the profile owner. Blocked members never reach a view, so no block flag is needed. */
public record RelationshipView(boolean self, boolean connected, boolean pendingIncoming, boolean pendingOutgoing,
        UUID requestId) {

    public static RelationshipView ofSelf() {
        return new RelationshipView(true, false, false, false, null);
    }
}
