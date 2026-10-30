package edu.diu.cyberconnect.networking.infrastructure;

import edu.diu.cyberconnect.common.model.ConnectionStatus;
import edu.diu.cyberconnect.networking.domain.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    @Query("""
            select c from Connection c where c.status in :statuses
              and ((c.requesterId = :a and c.addresseeId = :b) or (c.requesterId = :b and c.addresseeId = :a))""")
    Optional<Connection> findBetweenWithStatus(@Param("a") UUID a, @Param("b") UUID b,
            @Param("statuses") Collection<ConnectionStatus> statuses);

    @Query("""
            select c from Connection c
             where (c.requesterId = :a and c.addresseeId = :b) or (c.requesterId = :b and c.addresseeId = :a)""")
    List<Connection> findAllBetween(@Param("a") UUID a, @Param("b") UUID b);

    Page<Connection> findByAddresseeIdAndStatusOrderByCreatedAtDesc(UUID addresseeId, ConnectionStatus status,
            Pageable pageable);

    Page<Connection> findByRequesterIdAndStatusOrderByCreatedAtDesc(UUID requesterId, ConnectionStatus status,
            Pageable pageable);

    long countByAddresseeIdAndStatus(UUID addresseeId, ConnectionStatus status);

    long countByRequesterIdAndStatus(UUID requesterId, ConnectionStatus status);

    @Query(value = """
            select c from Connection c where c.status = :status
              and (c.requesterId = :userId or c.addresseeId = :userId) order by c.respondedAt desc""",
            countQuery = """
            select count(c) from Connection c where c.status = :status
              and (c.requesterId = :userId or c.addresseeId = :userId)""")
    Page<Connection> findInvolving(@Param("userId") UUID userId, @Param("status") ConnectionStatus status,
            Pageable pageable);

    @Query("""
            select count(c) from Connection c where c.status = :status
              and (c.requesterId = :userId or c.addresseeId = :userId)""")
    long countInvolving(@Param("userId") UUID userId, @Param("status") ConnectionStatus status);

    @Query("""
            select case when c.requesterId = :userId then c.addresseeId else c.requesterId end
              from Connection c where c.status = :status and (c.requesterId = :userId or c.addresseeId = :userId)""")
    List<UUID> partnerIds(@Param("userId") UUID userId, @Param("status") ConnectionStatus status);
}
