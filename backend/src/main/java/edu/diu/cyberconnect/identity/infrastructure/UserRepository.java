package edu.diu.cyberconnect.identity.infrastructure;

import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select count(u) from User u join u.roles r where r.role.name = :role")
    long countByRole(@Param("role") RoleName role);

    @Query("""
            select u from User u
            where (:status is null or u.accountStatus = :status)
              and (:role is null or exists (select 1 from UserRole ur where ur.user = u and ur.role.name = :role))
              and (:q is null or lower(u.email) like :q or lower(u.fullName) like :q)
            order by u.createdAt desc
            """)
    Page<User> search(@Param("q") String q, @Param("status") AccountStatus status, @Param("role") RoleName role,
            Pageable pageable);
}
