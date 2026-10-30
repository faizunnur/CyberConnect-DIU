package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.community.domain.PostLike;
import edu.diu.cyberconnect.community.domain.UserPostKey;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, UserPostKey> {

    @Query("select l.id.postId from PostLike l where l.id.userId = :userId and l.id.postId in :postIds")
    List<UUID> likedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}
