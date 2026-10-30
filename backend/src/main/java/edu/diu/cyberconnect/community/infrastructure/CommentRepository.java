package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.common.model.ContentStatus;
import edu.diu.cyberconnect.community.domain.Comment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Top-level comments that are active, or non-active ones that still have active replies (shown as placeholders). */
    @Query(value = """
            select c from Comment c where c.postId = :postId and c.parentId is null
              and (c.status = edu.diu.cyberconnect.common.model.ContentStatus.ACTIVE
                   or exists (select r from Comment r where r.parentId = c.id
                              and r.status = edu.diu.cyberconnect.common.model.ContentStatus.ACTIVE))
            order by c.createdAt asc""",
            countQuery = """
            select count(c) from Comment c where c.postId = :postId and c.parentId is null
              and (c.status = edu.diu.cyberconnect.common.model.ContentStatus.ACTIVE
                   or exists (select r from Comment r where r.parentId = c.id
                              and r.status = edu.diu.cyberconnect.common.model.ContentStatus.ACTIVE))""")
    Page<Comment> topLevel(@Param("postId") UUID postId, Pageable pageable);

    List<Comment> findByParentIdInAndStatusOrderByCreatedAtAsc(Collection<UUID> parentIds, ContentStatus status);

    long countByParentIdAndStatus(UUID parentId, ContentStatus status);
}
