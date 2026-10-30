package edu.diu.cyberconnect.identity.infrastructure;

import edu.diu.cyberconnect.identity.domain.MfaCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {
}
