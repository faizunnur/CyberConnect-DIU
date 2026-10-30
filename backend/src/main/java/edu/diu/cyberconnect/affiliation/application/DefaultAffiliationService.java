package edu.diu.cyberconnect.affiliation.application;

import edu.diu.cyberconnect.affiliation.domain.UserAffiliation;
import edu.diu.cyberconnect.affiliation.infrastructure.UserAffiliationRepository;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.AffiliationStatus;
import edu.diu.cyberconnect.common.model.AffiliationType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultAffiliationService implements AffiliationService {

    private final UserAffiliationRepository repository;
    private final EligibilityRules rules;

    public DefaultAffiliationService(UserAffiliationRepository repository, EligibilityRules rules) {
        this.repository = repository;
        this.rules = rules;
    }

    @Override
    public AffiliationSummary createPrimary(UUID userId, AffiliationType type, AffiliationDetails details) {
        rules.validate(type, details);
        if (!repository.findByUserIdOrderByCreatedAtAsc(userId).isEmpty()) {
            throw new ConflictException("PRIMARY_AFFILIATION_EXISTS", "A primary affiliation already exists.");
        }
        return toSummary(repository.save(UserAffiliation.create(userId, type, true, details.studentId(),
                details.batch(), details.department(), details.graduationYear(), details.cscMemberId())));
    }

    @Override
    public AffiliationSummary requestAdditional(UUID userId, AffiliationType type, AffiliationDetails details) {
        rules.validate(type, details);
        if (repository.findByUserIdAndType(userId, type).isPresent()) {
            throw new ConflictException("AFFILIATION_EXISTS", "You already hold or requested this affiliation.");
        }
        return toSummary(repository.save(UserAffiliation.create(userId, type, false, details.studentId(),
                details.batch(), details.department(), details.graduationYear(), details.cscMemberId())));
    }

    @Override
    public AffiliationSummary updateDetails(UUID userId, UUID affiliationId, AffiliationDetails details) {
        UserAffiliation affiliation = load(affiliationId);
        if (!affiliation.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_OWNER", "This affiliation does not belong to you.");
        }
        if (affiliation.getStatus() == AffiliationStatus.VERIFIED) {
            throw new ConflictException("AFFILIATION_VERIFIED", "Verified affiliation details cannot be edited.");
        }
        rules.validate(affiliation.getType(), details);
        affiliation.updateDetails(details.studentId(), details.batch(), details.department(), details.graduationYear(),
                details.cscMemberId());
        return toSummary(affiliation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AffiliationSummary> listForUser(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AffiliationSummary get(UUID affiliationId) {
        return toSummary(load(affiliationId));
    }

    @Override
    public void markVerified(UUID affiliationId, UUID submissionId) {
        load(affiliationId).markVerified(submissionId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasVerifiedAffiliation(UUID userId) {
        return repository.existsByUserIdAndStatus(userId, AffiliationStatus.VERIFIED);
    }

    private UserAffiliation load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("AFFILIATION_NOT_FOUND", "Affiliation not found."));
    }

    private AffiliationSummary toSummary(UserAffiliation a) {
        return new AffiliationSummary(a.getId(), a.getUserId(), a.getType(), a.getStatus(), a.isPrimary(),
                a.getStudentId(), a.getBatch(), a.getDepartment(), a.getGraduationYear(), a.getCscMemberId(),
                a.getVerifiedAt());
    }
}
