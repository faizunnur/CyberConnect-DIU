package edu.diu.cyberconnect.profile.infrastructure;

import edu.diu.cyberconnect.profile.domain.UserSkill;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, UserSkill.Key> {

    List<UserSkill> findByIdUserIdOrderByPositionAsc(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserSkill us where us.id.userId = :userId")
    void deleteAllForUser(@Param("userId") UUID userId);
}
