package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.community.domain.Hashtag;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HashtagRepository extends JpaRepository<Hashtag, UUID> {

    List<Hashtag> findByTagIn(Collection<String> tags);

    /** (postId, tag) pairs for the given posts. */
    @Query("select ph.id.postId, h.tag from PostHashtag ph join Hashtag h on h.id = ph.id.hashtagId where ph.id.postId in :postIds")
    List<Object[]> tagsForPosts(@Param("postIds") Collection<UUID> postIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PostHashtag ph where ph.id.postId = :postId")
    void unlinkAll(@Param("postId") UUID postId);
}
