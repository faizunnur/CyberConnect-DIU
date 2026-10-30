package edu.diu.cyberconnect.research.infrastructure;

import edu.diu.cyberconnect.research.domain.ResearchMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearchMemberRepository extends JpaRepository<ResearchMember, ResearchMember.Key> {

    List<ResearchMember> findByIdProjectIdOrderByJoinedAtAsc(UUID projectId);

    @Query("select m.id.projectId from ResearchMember m where m.id.userId = :userId")
    List<UUID> projectIdsOf(@Param("userId") UUID userId);
}
