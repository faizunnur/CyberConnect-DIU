package edu.diu.cyberconnect.moderation.application;

import edu.diu.cyberconnect.affiliation.application.AffiliationService;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.ForbiddenException;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.RoleName;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import edu.diu.cyberconnect.moderation.domain.ModerationAction;
import edu.diu.cyberconnect.moderation.infrastructure.ModerationActionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Member-level moderation: suspend/unsuspend (moderators), deactivate/reactivate/reopen (admins). */
@Service
@Transactional
public class MemberModerationService {

    private static final Logger log = LoggerFactory.getLogger(MemberModerationService.class);

    private final ModerationActionRepository actions;
    private final UserAccounts accounts;
    private final AffiliationService affiliations;

    public MemberModerationService(ModerationActionRepository actions, UserAccounts accounts,
            AffiliationService affiliations) {
        this.actions = actions;
        this.accounts = accounts;
        this.affiliations = affiliations;
    }

    public void suspend(UUID targetId, String reason, Instant expiresAt, UUID actorId) {
        UserSummary target = guard(targetId, actorId);
        if (target.accountStatus() != AccountStatus.VERIFIED) {
            throw new ConflictException("NOT_SUSPENDABLE", "Only verified accounts can be suspended.");
        }
        if (target.roles().contains(RoleName.SUPER_ADMIN)) {
            throw new ForbiddenException("CANNOT_MODERATE_SUPER_ADMIN", "A SUPER_ADMIN cannot be suspended.");
        }
        accounts.changeStatus(targetId, AccountStatus.SUSPENDED, actorId, reason);
        actions.save(new ModerationAction(actorId, targetId, ModerationAction.Type.SUSPEND, reason, expiresAt));
    }

    public void unsuspend(UUID targetId, String reason, UUID actorId) {
        UserSummary target = guard(targetId, actorId);
        if (target.accountStatus() != AccountStatus.SUSPENDED) {
            throw new ConflictException("NOT_SUSPENDED", "The account is not suspended.");
        }
        accounts.changeStatus(targetId, AccountStatus.VERIFIED, actorId, reason);
        actions.save(new ModerationAction(actorId, targetId, ModerationAction.Type.UNSUSPEND, reason, null));
    }

    public void deactivate(UUID targetId, String reason, UUID actorId) {
        UserSummary target = guard(targetId, actorId);
        if (target.roles().contains(RoleName.SUPER_ADMIN)) {
            throw new ForbiddenException("CANNOT_MODERATE_SUPER_ADMIN", "A SUPER_ADMIN cannot be deactivated here.");
        }
        accounts.changeStatus(targetId, AccountStatus.DEACTIVATED, actorId, reason);
        actions.save(new ModerationAction(actorId, targetId, ModerationAction.Type.DEACTIVATE, reason, null));
    }

    public void reactivate(UUID targetId, String reason, UUID actorId) {
        UserSummary target = guard(targetId, actorId);
        if (target.accountStatus() != AccountStatus.DEACTIVATED) {
            throw new ConflictException("NOT_DEACTIVATED", "The account is not deactivated.");
        }
        AccountStatus restored = affiliations.hasVerifiedAffiliation(targetId) ? AccountStatus.VERIFIED : AccountStatus.PENDING;
        accounts.changeStatus(targetId, restored, actorId, reason);
        actions.save(new ModerationAction(actorId, targetId, ModerationAction.Type.REACTIVATE, reason, null));
    }

    /** Clears the terminal lock after a denied appeal so the applicant may submit evidence again. */
    public void reopenVerification(UUID targetId, String reason, UUID actorId) {
        UserSummary target = guard(targetId, actorId);
        if (!target.verificationLocked()) {
            throw new ConflictException("NOT_LOCKED", "Verification is not locked for this account.");
        }
        accounts.unlockVerification(targetId, actorId, reason);
        actions.save(new ModerationAction(actorId, targetId, ModerationAction.Type.REOPEN_VERIFICATION, reason, null));
    }

    @Transactional(readOnly = true)
    public List<ModerationActionView> history(UUID targetId) {
        return actions.findByTargetUserIdOrderByCreatedAtDesc(targetId).stream().map(a -> new ModerationActionView(
                a.getId(), a.getActorId(), a.getTargetUserId(), a.getAction().name(), a.getReason(), a.getExpiresAt(),
                a.getCreatedAt())).toList();
    }

    /** Lifts suspensions whose expiry passed. Runs every 15 minutes; actions are recorded as system actions. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void liftExpiredSuspensions() {
        for (ModerationAction suspension : actions.findByActionAndExpiresAtBefore(ModerationAction.Type.SUSPEND, Instant.now())) {
            UserSummary user = accounts.get(suspension.getTargetUserId());
            if (user.accountStatus() != AccountStatus.SUSPENDED) {
                continue;
            }
            List<ModerationAction> history = actions.findByTargetUserIdOrderByCreatedAtDesc(user.id());
            if (!history.isEmpty() && history.get(0).getId().equals(suspension.getId())) {
                accounts.changeStatus(user.id(), AccountStatus.VERIFIED, null, "Suspension expired");
                actions.save(new ModerationAction(suspension.getActorId(), user.id(), ModerationAction.Type.UNSUSPEND,
                        "Suspension expired automatically", null));
                log.info("Lifted expired suspension for user {}", user.id());
            }
        }
    }

    private UserSummary guard(UUID targetId, UUID actorId) {
        if (targetId.equals(actorId)) {
            throw new ForbiddenException("SELF_MODERATION", "You cannot moderate your own account.");
        }
        return accounts.get(targetId);
    }
}
