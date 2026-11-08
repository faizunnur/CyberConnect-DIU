package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.community.domain.PostAttachment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, UUID> {

    @Query("select a from PostAttachment a where a.postId in :postIds order by a.postId, a.position")
    List<PostAttachment> findForPosts(@Param("postIds") Collection<UUID> postIds);

    List<PostAttachment> findByPostIdOrderByPosition(UUID postId);

    @Query("select a from PostAttachment a where a.postId is null and a.createdAt < :before")
    List<PostAttachment> findUnclaimedBefore(@Param("before") Instant before);

    @Query("select count(a) from PostAttachment a where a.ownerId = :ownerId and a.postId is null")
    long countUnclaimed(@Param("ownerId") UUID ownerId);
}
