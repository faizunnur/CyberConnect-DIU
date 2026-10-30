package edu.diu.cyberconnect.impact.infrastructure;

import edu.diu.cyberconnect.common.model.ImpactStatus;
import edu.diu.cyberconnect.common.model.ImpactType;
import edu.diu.cyberconnect.impact.domain.ImpactRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImpactRecordRepository extends JpaRepository<ImpactRecord, UUID> {

    @Query("""
            select r from ImpactRecord r where (r.beneficiaryId = :userId or r.contributorId = :userId)
              and (:status is null or r.status = :status) order by r.updatedAt desc""")
    Page<ImpactRecord> involving(@Param("userId") UUID userId, @Param("status") ImpactStatus status, Pageable pageable);

    @Query("""
            select r from ImpactRecord r where (r.beneficiaryId = :userId or r.contributorId = :userId)
              and r.status = edu.diu.cyberconnect.common.model.ImpactStatus.CONFIRMED order by r.confirmedAt desc""")
    List<ImpactRecord> confirmedInvolving(@Param("userId") UUID userId, Pageable pageable);

    Page<ImpactRecord> findByStatusOrderByUpdatedAtAsc(ImpactStatus status, Pageable pageable);

    @Query("select count(r) from ImpactRecord r where r.status = :status")
    long countByStatus(@Param("status") ImpactStatus status);

    @Query("select r.type, count(r) from ImpactRecord r where r.status = edu.diu.cyberconnect.common.model.ImpactStatus.CONFIRMED group by r.type")
    List<Object[]> confirmedByType();

    @Query("select count(distinct r.contributorId) from ImpactRecord r where r.status = edu.diu.cyberconnect.common.model.ImpactStatus.CONFIRMED")
    long distinctContributors();

    @Query("select count(distinct r.beneficiaryId) from ImpactRecord r where r.status = edu.diu.cyberconnect.common.model.ImpactStatus.CONFIRMED")
    long distinctBeneficiaries();

    @Query("""
            select r.type, count(r) from ImpactRecord r where r.contributorId = :userId
              and r.status = edu.diu.cyberconnect.common.model.ImpactStatus.CONFIRMED group by r.type""")
    List<Object[]> confirmedAsContributorByType(@Param("userId") UUID userId);

    @Query("""
            select count(r) from ImpactRecord r where r.beneficiaryId = :userId
              and r.status = edu.diu.cyberconnect.common.model.ImpactStatus.CONFIRMED""")
    long confirmedAsBeneficiary(@Param("userId") UUID userId);

    @Query("""
            select r from ImpactRecord r where r.status <> edu.diu.cyberconnect.common.model.ImpactStatus.REJECTED
              and r.type = :type and r.beneficiaryId = :beneficiary and r.contributorId = :contributor
              and ((r.type = edu.diu.cyberconnect.common.model.ImpactType.JOB_REFERRAL and r.applicationId = :source)
                or (r.type = edu.diu.cyberconnect.common.model.ImpactType.RESEARCH and r.researchProjectId = :source)
                or (r.type = edu.diu.cyberconnect.common.model.ImpactType.MENTORSHIP and r.mentorshipRequestId = :source))""")
    List<ImpactRecord> live(@Param("type") ImpactType type, @Param("source") UUID source,
            @Param("beneficiary") UUID beneficiary, @Param("contributor") UUID contributor);
}
