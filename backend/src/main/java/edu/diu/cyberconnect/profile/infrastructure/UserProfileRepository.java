package edu.diu.cyberconnect.profile.infrastructure;

import edu.diu.cyberconnect.profile.domain.UserProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
