package edu.diu.cyberconnect.research.infrastructure;

import edu.diu.cyberconnect.research.domain.ResearchProject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearchProjectRepository extends JpaRepository<ResearchProject, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update research_projects set member_count = greatest(member_count + :delta, 0) where id = :id",
            nativeQuery = true)
    void adjustMembers(@Param("id") UUID id, @Param("delta") int delta);
}
