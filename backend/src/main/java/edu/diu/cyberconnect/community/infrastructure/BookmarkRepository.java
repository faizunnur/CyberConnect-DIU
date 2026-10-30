package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.community.domain.Bookmark;
import edu.diu.cyberconnect.community.domain.UserPostKey;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, UserPostKey> {

    @Query("select b.id.postId from Bookmark b where b.id.userId = :userId and b.id.postId in :postIds")
    List<UUID> bookmarkedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}
