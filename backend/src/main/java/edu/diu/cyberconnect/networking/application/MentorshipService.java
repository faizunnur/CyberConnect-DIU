package edu.diu.cyberconnect.networking.application;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.MentorshipStatus;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.common.text.PlainText;
import edu.diu.cyberconnect.networking.domain.MentorshipRequest;
import edu.diu.cyberconnect.networking.infrastructure.MentorshipRequestRepository;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.ProfileView;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mentorship requests between a mentee and a member who advertises availability on their profile (D-10). */
@Service
@Transactional
public class MentorshipService {

    static final int REQUESTS_PER_DAY = 10;
    private static final Set<MentorshipStatus> LIVE = EnumSet.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED);

    private final MentorshipRequestRepository requests;
    private final ProfileService profiles;
    private final RateLimiter rateLimiter;

    public MentorshipService(MentorshipRequestRepository requests, ProfileService profiles, RateLimiter rateLimiter) {
        this.requests = requests;
        this.profiles = profiles;
        this.rateLimiter = rateLimiter;
    }

    public MentorshipRequestView request(UUID menteeId, UUID mentorId, String rawTopic, String rawMessage) {
        if (menteeId.equals(mentorId)) {
            throw new BadRequestException("SELF_MENTORSHIP", "You cannot request mentorship from yourself.");
        }
        ProfileView mentor = profiles.view(menteeId, mentorId); // 404 when blocked, unknown or not verified
        if (!Boolean.TRUE.equals(mentor.mentorAvailable())) {
            throw new ConflictException("MENTOR_UNAVAILABLE", "This member is not offering mentorship right now.");
        }
        String topic = PlainText.sanitize(rawTopic);
        String message = PlainText.sanitize(rawMessage);
        if (topic.isEmpty() || message.isEmpty()) {
            throw new BadRequestException("BODY_REQUIRED", "Say what you would like help with and why.");
        }
        if (topic.length() > 160 || message.length() > 2000) {
            throw new BadRequestException("BODY_TOO_LONG", "Keep the topic under 160 and the message under 2000 characters.");
        }
        Optional<MentorshipRequest> live = requests.findLive(menteeId, mentorId, LIVE);
        if (live.isPresent()) {
            throw new ConflictException(live.get().getStatus() == MentorshipStatus.PENDING ? "REQUEST_PENDING" : "ALREADY_MENTORING",
                    "You already have an open mentorship with this member.");
        }
        rateLimiter.check("mentorship-request", menteeId.toString(), REQUESTS_PER_DAY, Duration.ofDays(1));
        MentorshipRequest created = requests.save(MentorshipRequest.create(menteeId, mentorId, topic, message));
        return view(menteeId, created);
    }

    public MentorshipRequestView respond(UUID mentorId, UUID requestId, boolean accept, String rawNote) {
        MentorshipRequest request = load(requestId, mentorId);
        if (!request.getMentorId().equals(mentorId) || request.getStatus() != MentorshipStatus.PENDING) {
            throw new ConflictException("REQUEST_NOT_PENDING", "This request is no longer pending.");
        }
        String note = PlainText.sanitize(rawNote);
        request.respond(accept, note.isEmpty() ? null : note);
        return view(mentorId, request);
    }

    public MentorshipRequestView complete(UUID userId, UUID requestId) {
        MentorshipRequest request = load(requestId, userId);
        if (request.getStatus() != MentorshipStatus.ACCEPTED) {
            throw new ConflictException("NOT_ACTIVE", "Only an accepted mentorship can be completed.");
        }
        request.complete();
        return view(userId, request);
    }

    public void cancel(UUID menteeId, UUID requestId) {
        MentorshipRequest request = load(requestId, menteeId);
        if (!request.getMenteeId().equals(menteeId) || request.getStatus() != MentorshipStatus.PENDING) {
            throw new ConflictException("REQUEST_NOT_PENDING", "Only your own pending requests can be cancelled.");
        }
        request.cancel();
    }

    @Transactional(readOnly = true)
    public MentorshipRequestView get(UUID userId, UUID requestId) {
        return view(userId, load(requestId, userId));
    }

    @Transactional(readOnly = true)
    public Page<MentorshipRequestView> list(UUID userId, boolean asMentor, MentorshipStatus status, Pageable pageable) {
        Page<MentorshipRequest> page;
        if (asMentor) {
            page = status == null ? requests.findByMentorIdOrderByCreatedAtDesc(userId, pageable)
                    : requests.findByMentorIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        } else {
            page = status == null ? requests.findByMenteeIdOrderByCreatedAtDesc(userId, pageable)
                    : requests.findByMenteeIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        }
        List<UUID> people = page.getContent().stream().map(r -> r.getMentorId().equals(userId) ? r.getMenteeId() : r.getMentorId()).toList();
        Map<UUID, MemberCard> cards = profiles.cards(userId, people);
        MemberCard self = profiles.cards(userId, List.of(userId)).get(userId);
        return page.map(r -> view(userId, r, cards, self));
    }

    @Transactional(readOnly = true)
    public long pendingForMentor(UUID mentorId) {
        return requests.countByMentorIdAndStatus(mentorId, MentorshipStatus.PENDING);
    }

    private MentorshipRequest load(UUID requestId, UUID userId) {
        return requests.findById(requestId).filter(r -> r.involves(userId))
                .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Mentorship request not found."));
    }

    private MentorshipRequestView view(UUID viewerId, MentorshipRequest r) {
        Map<UUID, MemberCard> cards = profiles.cards(viewerId, List.of(r.getMenteeId(), r.getMentorId()));
        return new MentorshipRequestView(r.getId(), cards.get(r.getMenteeId()), cards.get(r.getMentorId()), r.getStatus(),
                r.getTopic(), r.getMessage(), r.getResponseNote(), r.getMentorId().equals(viewerId), r.getCreatedAt(),
                r.getRespondedAt(), r.getCompletedAt());
    }

    private static MentorshipRequestView view(UUID viewerId, MentorshipRequest r, Map<UUID, MemberCard> others, MemberCard self) {
        boolean incoming = r.getMentorId().equals(viewerId);
        MemberCard mentee = incoming ? others.get(r.getMenteeId()) : self;
        MemberCard mentor = incoming ? self : others.get(r.getMentorId());
        return new MentorshipRequestView(r.getId(), mentee, mentor, r.getStatus(), r.getTopic(), r.getMessage(),
                r.getResponseNote(), incoming, r.getCreatedAt(), r.getRespondedAt(), r.getCompletedAt());
    }
}
