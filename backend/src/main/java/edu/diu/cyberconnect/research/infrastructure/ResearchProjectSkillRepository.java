package edu.diu.cyberconnect.research.infrastructure;

import edu.diu.cyberconnect.research.domain.ResearchProjectSkill;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearchProjectSkillRepository extends JpaRepository<ResearchProjectSkill, ResearchProjectSkill.Key> {

    List<ResearchProjectSkill> findByIdProjectIdIn(Collection<UUID> projectIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResearchProjectSkill s where s.id.projectId = :projectId")
    void deleteAllFor(@Param("projectId") UUID projectId);
}
