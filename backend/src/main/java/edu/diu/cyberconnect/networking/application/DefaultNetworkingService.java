package edu.diu.cyberconnect.networking.application;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import edu.diu.cyberconnect.common.exception.ConflictException;
import edu.diu.cyberconnect.common.exception.NotFoundException;
import edu.diu.cyberconnect.common.model.AccountStatus;
import edu.diu.cyberconnect.common.model.ConnectionStatus;
import edu.diu.cyberconnect.common.ratelimit.RateLimiter;
import edu.diu.cyberconnect.identity.application.UserAccounts;
import edu.diu.cyberconnect.identity.application.UserSummary;
import edu.diu.cyberconnect.networking.domain.Block;
import edu.diu.cyberconnect.networking.domain.Connection;
import edu.diu.cyberconnect.networking.infrastructure.BlockRepository;
import edu.diu.cyberconnect.networking.infrastructure.ConnectionRepository;
import edu.diu.cyberconnect.profile.application.MemberCard;
import edu.diu.cyberconnect.profile.application.ProfileService;
import edu.diu.cyberconnect.profile.application.Relationship;
import edu.diu.cyberconnect.profile.application.RelationshipLookup;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultNetworkingService implements NetworkingService, RelationshipLookup {

    static final int REQUESTS_PER_HOUR = 30;
    private static final Set<ConnectionStatus> LIVE = EnumSet.of(ConnectionStatus.PENDING, ConnectionStatus.ACCEPTED);

    private final ConnectionRepository connections;
    private final BlockRepository blocks;
    private final UserAccounts accounts;
    private final ProfileService profiles;
    private final RateLimiter rateLimiter;

    public DefaultNetworkingService(ConnectionRepository connections, BlockRepository blocks, UserAccounts accounts,
            ProfileService profiles, RateLimiter rateLimiter) {
        this.connections = connections;
        this.blocks = blocks;
        this.accounts = accounts;
        this.profiles = profiles;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public ConnectionRequestView request(UUID requesterId, UUID addresseeId, String message) {
        if (requesterId.equals(addresseeId)) {
            throw new BadRequestException("SELF_CONNECTION", "You cannot connect with yourself.");
        }
        requireReachable(requesterId, addresseeId);
        Optional<Connection> live = connections.findBetweenWithStatus(requesterId, addresseeId, LIVE);
        if (live.isPresent()) {
            Connection existing = live.get();
            if (existing.getStatus() == ConnectionStatus.ACCEPTED) {
                throw new ConflictException("ALREADY_CONNECTED", "You are already connected.");
            }
            if (existing.getRequesterId().equals(requesterId)) {
                throw new ConflictException("REQUEST_PENDING", "Your request is still pending.");
            }
            // The other member already asked: treat this as acceptance rather than a reversed duplicate.
            existing.accept();
            return toRequestView(requesterId, existing);
        }
        rateLimiter.check("connection-request", requesterId.toString(), REQUESTS_PER_HOUR, Duration.ofHours(1));
        Connection created = connections.save(Connection.request(requesterId, addresseeId, message));
        return toRequestView(requesterId, created);
    }

    @Override
    public void accept(UUID userId, UUID requestId) {
        Connection request = pendingFor(userId, requestId, true);
        request.accept();
    }

    @Override
    public void decline(UUID userId, UUID requestId) {
        Connection request = pendingFor(userId, requestId, true);
        request.decline();
    }

    @Override
    public void cancel(UUID userId, UUID requestId) {
        Connection request = pendingFor(userId, requestId, false);
        connections.delete(request);
    }

    @Override
    public void remove(UUID userId, UUID otherId) {
        Connection connection = connections.findBetweenWithStatus(userId, otherId, EnumSet.of(ConnectionStatus.ACCEPTED))
                .orElseThrow(() -> new NotFoundException("NOT_CONNECTED", "You are not connected with this member."));
        connections.delete(connection);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConnectionView> connections(UUID userId, Pageable pageable) {
        Page<Connection> page = connections.findInvolving(userId, ConnectionStatus.ACCEPTED, pageable);
        Map<UUID, MemberCard> cards = profiles.cards(userId, page.map(c -> c.otherParty(userId)).toList());
        return page.map(c -> new ConnectionView(c.getId(), cards.get(c.otherParty(userId)), c.getRespondedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConnectionRequestView> incoming(UUID userId, Pageable pageable) {
        Page<Connection> page = connections.findByAddresseeIdAndStatusOrderByCreatedAtDesc(userId,
                ConnectionStatus.PENDING, pageable);
        Map<UUID, MemberCard> cards = profiles.cards(userId, page.map(Connection::getRequesterId).toList());
        return page.map(c -> toRequestView(userId, c, cards.get(c.getRequesterId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConnectionRequestView> outgoing(UUID userId, Pageable pageable) {
        Page<Connection> page = connections.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId,
                ConnectionStatus.PENDING, pageable);
        Map<UUID, MemberCard> cards = profiles.cards(userId, page.map(Connection::getAddresseeId).toList());
        return page.map(c -> toRequestView(userId, c, cards.get(c.getAddresseeId())));
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkSummary summary(UUID userId) {
        return new NetworkSummary(connections.countInvolving(userId, ConnectionStatus.ACCEPTED),
                connections.countByAddresseeIdAndStatus(userId, ConnectionStatus.PENDING),
                connections.countByRequesterIdAndStatus(userId, ConnectionStatus.PENDING),
                blocks.countByIdBlockerId(userId));
    }

    @Override
    public void block(UUID userId, UUID targetId) {
        if (userId.equals(targetId)) {
            throw new BadRequestException("SELF_BLOCK", "You cannot block yourself.");
        }
        try {
            accounts.get(targetId);
        } catch (NotFoundException e) {
            throw memberNotFound();
        }
        Block.Key key = new Block.Key(userId, targetId);
        if (!blocks.existsById(key)) {
            blocks.save(Block.of(userId, targetId));
        }
        connections.deleteAll(connections.findAllBetween(userId, targetId));
    }

    @Override
    public void unblock(UUID userId, UUID targetId) {
        blocks.findById(new Block.Key(userId, targetId)).ifPresent(blocks::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCard> blocked(UUID userId) {
        List<UUID> ids = blocks.findByIdBlockerIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> b.getId().getBlockedId()).toList();
        Map<UUID, MemberCard> cards = profiles.cards(userId, ids);
        return ids.stream().map(cards::get).filter(c -> c != null).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isConnected(UUID a, UUID b) {
        return connections.findBetweenWithStatus(a, b, EnumSet.of(ConnectionStatus.ACCEPTED)).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID a, UUID b) {
        return !blocks.findBetween(a, b).isEmpty();
    }

    // RelationshipLookup (port of the profile module) --------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Relationship between(UUID viewerId, UUID targetId) {
        boolean blockedByViewer = false;
        boolean blockedViewer = false;
        for (Block block : blocks.findBetween(viewerId, targetId)) {
            if (block.getId().getBlockerId().equals(viewerId)) {
                blockedByViewer = true;
            } else {
                blockedViewer = true;
            }
        }
        Optional<Connection> live = connections.findBetweenWithStatus(viewerId, targetId, LIVE);
        if (live.isEmpty()) {
            return new Relationship(false, blockedByViewer, blockedViewer, false, false, null);
        }
        Connection c = live.get();
        boolean connected = c.getStatus() == ConnectionStatus.ACCEPTED;
        boolean pending = c.getStatus() == ConnectionStatus.PENDING;
        return new Relationship(connected, blockedByViewer, blockedViewer,
                pending && c.getAddresseeId().equals(viewerId), pending && c.getRequesterId().equals(viewerId),
                pending ? c.getId() : null);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> blockedEitherWay(UUID userId) {
        return new HashSet<>(blocks.counterpartsOf(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> connectedWith(UUID userId) {
        return new HashSet<>(connections.partnerIds(userId, ConnectionStatus.ACCEPTED));
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Target must be a verified member and neither side may have blocked the other; otherwise a uniform 404. */
    private void requireReachable(UUID userId, UUID targetId) {
        UserSummary target;
        try {
            target = accounts.get(targetId);
        } catch (NotFoundException e) {
            throw memberNotFound();
        }
        if (target.accountStatus() != AccountStatus.VERIFIED || !blocks.findBetween(userId, targetId).isEmpty()) {
            throw memberNotFound();
        }
    }

    private Connection pendingFor(UUID userId, UUID requestId, boolean asAddressee) {
        Connection request = connections.findById(requestId).orElseThrow(DefaultNetworkingService::requestNotFound);
        UUID expected = asAddressee ? request.getAddresseeId() : request.getRequesterId();
        if (request.getStatus() != ConnectionStatus.PENDING || !expected.equals(userId)) {
            throw requestNotFound();
        }
        return request;
    }

    private ConnectionRequestView toRequestView(UUID viewerId, Connection c) {
        UUID other = c.otherParty(viewerId);
        return toRequestView(viewerId, c, profiles.cards(viewerId, List.of(other)).get(other));
    }

    private static ConnectionRequestView toRequestView(UUID viewerId, Connection c, MemberCard other) {
        return new ConnectionRequestView(c.getId(), other, c.getMessage(), c.getStatus(),
                c.getAddresseeId().equals(viewerId), c.getCreatedAt());
    }

    private static NotFoundException memberNotFound() {
        return new NotFoundException("MEMBER_NOT_FOUND", "Member not found.");
    }

    private static NotFoundException requestNotFound() {
        return new NotFoundException("REQUEST_NOT_FOUND", "Connection request not found.");
    }
}
