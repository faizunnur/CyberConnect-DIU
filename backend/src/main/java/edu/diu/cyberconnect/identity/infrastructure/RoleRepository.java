package edu.diu.cyberconnect.identity.infrastructure;

import edu.diu.cyberconnect.identity.domain.Role;
import edu.diu.cyberconnect.identity.domain.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
