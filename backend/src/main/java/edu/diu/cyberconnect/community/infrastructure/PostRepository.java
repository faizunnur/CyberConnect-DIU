package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.community.domain.Post;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update posts set like_count = greatest(like_count + :delta, 0) where id = :id", nativeQuery = true)
    void adjustLikes(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update posts set comment_count = greatest(comment_count + :delta, 0) where id = :id", nativeQuery = true)
    void adjustComments(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update posts set repost_count = greatest(repost_count + :delta, 0) where id = :id", nativeQuery = true)
    void adjustReposts(@Param("id") UUID id, @Param("delta") int delta);
}
