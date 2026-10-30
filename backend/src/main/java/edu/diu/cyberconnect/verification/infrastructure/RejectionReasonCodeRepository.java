package edu.diu.cyberconnect.verification.infrastructure;

import edu.diu.cyberconnect.verification.domain.RejectionReasonCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectionReasonCodeRepository extends JpaRepository<RejectionReasonCode, String> {

    List<RejectionReasonCode> findAllByOrderBySortOrderAsc();
}
