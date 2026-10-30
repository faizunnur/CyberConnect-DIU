package edu.diu.cyberconnect.identity.infrastructure;

import edu.diu.cyberconnect.common.model.RoleName;

import edu.diu.cyberconnect.identity.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
