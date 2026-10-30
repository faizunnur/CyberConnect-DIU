package edu.diu.cyberconnect.opportunity.infrastructure;

import edu.diu.cyberconnect.opportunity.domain.OpportunitySkill;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunitySkillRepository extends JpaRepository<OpportunitySkill, OpportunitySkill.Key> {

    List<OpportunitySkill> findByIdOpportunityIdIn(Collection<UUID> opportunityIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OpportunitySkill s where s.id.opportunityId = :opportunityId")
    void deleteAllFor(@Param("opportunityId") UUID opportunityId);
}
