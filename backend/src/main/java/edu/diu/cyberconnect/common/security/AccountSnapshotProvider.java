package edu.diu.cyberconnect.common.security;

import java.util.Optional;
import java.util.UUID;

/** Implemented by the identity module; lets the security layer re-check account state without a module dependency. */
public interface AccountSnapshotProvider {

    Optional<UserPrincipal.AccountSnapshot> snapshot(UUID userId);
}
