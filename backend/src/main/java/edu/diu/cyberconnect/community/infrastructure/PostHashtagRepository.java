package edu.diu.cyberconnect.community.infrastructure;

import edu.diu.cyberconnect.community.domain.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtag.Key> {
}
