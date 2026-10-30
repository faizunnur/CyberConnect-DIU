package edu.diu.cyberconnect.networking.infrastructure;

import edu.diu.cyberconnect.networking.domain.Block;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, Block.Key> {

    List<Block> findByIdBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    long countByIdBlockerId(UUID blockerId);

    @Query("""
            select b from Block b
             where (b.id.blockerId = :a and b.id.blockedId = :b) or (b.id.blockerId = :b and b.id.blockedId = :a)""")
    List<Block> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("""
            select case when b.id.blockerId = :userId then b.id.blockedId else b.id.blockerId end
              from Block b where b.id.blockerId = :userId or b.id.blockedId = :userId""")
    List<UUID> counterpartsOf(@Param("userId") UUID userId);
}
