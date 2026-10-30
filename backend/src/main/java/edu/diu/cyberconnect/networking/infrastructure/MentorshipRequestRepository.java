package edu.diu.cyberconnect.networking.infrastructure;

import edu.diu.cyberconnect.common.model.MentorshipStatus;
import edu.diu.cyberconnect.networking.domain.MentorshipRequest;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentorshipRequestRepository extends JpaRepository<MentorshipRequest, UUID> {

    @Query("select r from MentorshipRequest r where r.menteeId = :mentee and r.mentorId = :mentor and r.status in :statuses")
    Optional<MentorshipRequest> findLive(@Param("mentee") UUID menteeId, @Param("mentor") UUID mentorId,
            @Param("statuses") Collection<MentorshipStatus> statuses);

    Page<MentorshipRequest> findByMenteeIdOrderByCreatedAtDesc(UUID menteeId, Pageable pageable);

    Page<MentorshipRequest> findByMentorIdOrderByCreatedAtDesc(UUID mentorId, Pageable pageable);

    Page<MentorshipRequest> findByMenteeIdAndStatusOrderByCreatedAtDesc(UUID menteeId, MentorshipStatus status, Pageable pageable);

    Page<MentorshipRequest> findByMentorIdAndStatusOrderByCreatedAtDesc(UUID mentorId, MentorshipStatus status, Pageable pageable);

    long countByMentorIdAndStatus(UUID mentorId, MentorshipStatus status);
}
