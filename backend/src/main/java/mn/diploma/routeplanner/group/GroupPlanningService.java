package mn.diploma.routeplanner.group;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.AuthDtos.PreferencesDto;
import mn.diploma.routeplanner.auth.AuthService;
import mn.diploma.routeplanner.auth.ConsentDataType;
import mn.diploma.routeplanner.auth.ConsentService;
import mn.diploma.routeplanner.auth.UserDeletedEvent;
import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.common.TransportMode;
import mn.diploma.routeplanner.friend.FriendService;
import mn.diploma.routeplanner.group.GroupDtos.CreateGroupRequest;
import mn.diploma.routeplanner.group.GroupDtos.GroupView;
import mn.diploma.routeplanner.group.GroupDtos.JoinInfo;
import mn.diploma.routeplanner.group.GroupDtos.JoinRequest;
import mn.diploma.routeplanner.group.GroupDtos.JoinResult;
import mn.diploma.routeplanner.group.GroupDtos.MemberStart;
import mn.diploma.routeplanner.group.GroupDtos.MemberView;
import mn.diploma.routeplanner.group.GroupDtos.ModifyMembersRequest;
import mn.diploma.routeplanner.group.GroupDtos.OptionView;
import mn.diploma.routeplanner.group.GroupDtos.OptionsView;
import mn.diploma.routeplanner.group.GroupDtos.PlaceRef;
import mn.diploma.routeplanner.group.GroupRepository.Member;
import mn.diploma.routeplanner.group.GroupRepository.Session;
import mn.diploma.routeplanner.recommendation.GroupRecommender;
import mn.diploma.routeplanner.recommendation.RecommendationService;
import mn.diploma.routeplanner.routing.LatLng;
import mn.diploma.routeplanner.routing.RoutingService;
import mn.diploma.routeplanner.routing.TravelTimeEstimator;

/**
 * Бүлгийн төлөвлөгөө (АШ10, АШ24): санаачлагч найзуудаа сонгож (зөвшөөрөл хүлээхгүйгээр нэмнэ), бүгдийн
 * сонирхолд нийцсэн, хүрэхэд шударга 3-5 уулзах газрын санал авч, нэгийг нь хуваалцана; гишүүн бүр өөрийн
 * Google Maps-ийн холбоосыг авна. Бүртгэлгүй хүмүүс урилгын холбоосоор зочноор нэгдэнэ.
 */
@Service
public class GroupPlanningService {

    static final int OPTIONS = 5;
    static final int MAX_MEMBERS = 10;

    private final GroupRepository groups;
    private final FriendService friends;
    private final MemberStartResolver starts;
    private final GroupRecommender recommender;
    private final RoutingService routing;
    private final TravelTimeEstimator travelTimes;
    private final GroupInviteService invites;
    private final MemberLinkService links;
    private final AuthService auth;
    private final ConsentService consents;
    private final JdbcTemplate jdbc;

    public GroupPlanningService(GroupRepository groups, FriendService friends, MemberStartResolver starts,
                                GroupRecommender recommender, RoutingService routing, TravelTimeEstimator travelTimes,
                                GroupInviteService invites, MemberLinkService links, AuthService auth,
                                ConsentService consents, JdbcTemplate jdbc) {
        this.groups = groups;
        this.friends = friends;
        this.starts = starts;
        this.recommender = recommender;
        this.routing = routing;
        this.travelTimes = travelTimes;
        this.invites = invites;
        this.links = links;
        this.auth = auth;
        this.consents = consents;
        this.jdbc = jdbc;
    }

    @Transactional
    public GroupView createSession(long initiatorId, CreateGroupRequest req) {
        Set<Long> friendIds = new LinkedHashSet<>(req.friendIds());
        friendIds.remove(initiatorId);
        if (friendIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_MEMBERS", "Дор хаяж нэг найзаа сонгоно уу");
        }
        List<Long> accepted = friends.friendIds(initiatorId);
        for (long f : friendIds) {
            if (!accepted.contains(f)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_FRIEND", "Зөвхөн найзуудаа нэмэх боломжтой");
            }
        }
        PreferencesDto prefs = auth.preferences(initiatorId);
        long sessionId = groups.insertSession(
                req.timeBudgetMinutes() != null ? req.timeBudgetMinutes() : prefs.timeBudgetMin(),
                req.budgetLevel() != null ? req.budgetLevel() : prefs.budgetLevel());

        Member initiator = new Member();
        initiator.userId = initiatorId;
        initiator.role = MemberRole.INITIATOR;
        initiator.startLat = req.lat();
        initiator.startLng = req.lng();
        initiator.startSource = StartSource.PINNED;
        initiator.transportMode = req.transportMode() != null ? req.transportMode() : firstMode(prefs);
        initiator.usesInterests = consents.isGranted(initiatorId, ConsentDataType.INTERESTS);
        groups.insertMember(sessionId, initiator);

        for (long f : friendIds) {
            MemberStart pin = req.starts() == null ? null
                    : req.starts().stream().filter(s -> s.userId() == f).findFirst().orElse(null);
            addFriend(sessionId, f, pin == null ? null : pin.lat(), pin == null ? null : pin.lng(),
                    pin == null ? null : pin.transportMode(), req.lat(), req.lng());
        }
        return view(initiatorId, load(sessionId));
    }

    @Transactional(readOnly = true)
    public List<GroupView> list(long userId) {
        return groups.findByMemberUser(userId).stream().map(s -> view(userId, s)).toList();
    }

    @Transactional(readOnly = true)
    public GroupView get(long userId, long sessionId) {
        Session s = load(sessionId);
        if (s.memberOfUser(userId).isEmpty()) throw notFound();
        return view(userId, s);
    }

    @Transactional
    public GroupView modifyMembers(long userId, long sessionId, ModifyMembersRequest req) {
        Session s = ownedOpen(userId, sessionId);
        Member init = s.initiator();
        switch (req.op()) {
            case ADD -> {
                if (req.userId() == null || !friends.areFriends(userId, req.userId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_FRIEND", "Зөвхөн найзуудаа нэмэх боломжтой");
                }
                if (s.memberOfUser(req.userId()).isPresent()) {
                    throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Аль хэдийн бүлэгт байна");
                }
                if (s.members.size() >= MAX_MEMBERS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_MEMBERS", "Бүлэг хамгийн ихдээ " + MAX_MEMBERS + " хүнтэй");
                }
                addFriend(sessionId, req.userId(), req.lat(), req.lng(), req.transportMode(), init.startLat, init.startLng);
            }
            case REMOVE -> {
                Member m = member(s, req.memberId());
                if (m.role == MemberRole.INITIATOR) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REMOVE_INITIATOR", "Санаачлагчийг хасах боломжгүй");
                }
                groups.deleteMember(m.id);
            }
            case SET_START -> {
                Member m = member(s, req.memberId());
                boolean moved = req.lat() != null && req.lng() != null;
                groups.updateMemberStart(m.id, req.lat(), req.lng(), moved ? StartSource.PINNED : null,
                        req.transportMode() != null ? req.transportMode() : m.transportMode);
            }
        }
        return view(userId, load(sessionId));
    }

    /** Гишүүн бүрийн зорчих хугацаатай 3-5 уулзах газар (бүлгийн санал болголтын багц болгон бүртгэнэ). */
    @Transactional
    public OptionsView getOptions(long userId, long sessionId, boolean again) {
        Session s = load(sessionId);
        if (s.initiator().userId == null || s.initiator().userId != userId) throw notFound();
        List<GroupRecommender.MemberInput> inputs = s.members.stream().filter(Member::hasStart)
                .map(m -> new GroupRecommender.MemberInput(m.id, m.userId, m.usesInterests, m.startLat, m.startLng,
                        m.transportMode))
                .toList();
        GroupRecommender.Result result = recommender.recommend(new GroupRecommender.Request(sessionId, userId, inputs,
                s.timeBudgetMinutes, s.budgetLevel, LocalDateTime.now(RecommendationService.ZONE), OPTIONS, again));
        return new OptionsView(result.batchId(), result.aggregator(), result.options().stream()
                .map(o -> new OptionView(o.recommendationId(),
                        new PlaceRef(o.placeId(), o.name(), o.categoryName(), o.mainCategory(), o.lat(), o.lng()),
                        o.score(), o.travelMinutesByMember(), o.maxTravelMinutes()))
                .toList());
    }

    /**
     * RECOMMENDED -> SHARED: уулзах газрыг тогтоож, бүх гишүүний зорчих хугацааг тооцож, урилгын холбоос болон
     * бүртгэлтэй гишүүн бүрд нэг зогсоолтой маршрут үүсгэнэ.
     */
    @Transactional
    public GroupView share(long userId, long sessionId, long placeId) {
        Session s = ownedOpen(userId, sessionId);
        PlaceRef place = place(placeId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Газар олдсонгүй"));
        LatLng dest = new LatLng(place.lat(), place.lng());
        groups.share(sessionId, placeId, invites.createToken(), invites.expiry());
        for (Member m : s.members) {
            if (!m.hasStart()) continue;
            groups.updateTravel(m.id, travel(m, dest));
            if (m.userId != null) {
                routing.createGroupRoute(m.userId, new LatLng(m.startLat, m.startLng), m.transportMode,
                        s.timeBudgetMinutes, placeId, sessionId);
            }
        }
        return view(userId, load(sessionId));
    }

    @Transactional
    public void cancel(long userId, long sessionId) {
        Session s = load(sessionId);
        if (s.initiator().userId == null || s.initiator().userId != userId) throw notFound();
        if (s.status != GroupStatus.RECOMMENDED && s.status != GroupStatus.SHARED) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_CLOSED", "Төлөвлөгөө дууссан эсвэл цуцлагдсан");
        }
        groups.close(sessionId, GroupStatus.CANCELLED);
        routing.cancelGroupRoutes(sessionId);
    }

    @Transactional(readOnly = true)
    public JoinInfo joinInfo(String token) {
        Session s = groups.findByInviteToken(token).orElseThrow(GroupPlanningService::invalidInvite);
        return new JoinInfo(s.status, s.initiator().displayName, s.members.size(),
                s.meetingPlaceId == null ? null : place(s.meetingPlaceId).orElse(null), s.inviteExpiresAt,
                s.isInviteValid(Instant.now()));
    }

    /**
     * Урилгын холбоосоор нэгдэх (АШ24). Нэвтэрсэн хэрэглэгч гишүүнээр нэгдэж маршрут авна; бусад нь зочноор
     * нэгдэх бөгөөд сонирхлыг нь тооцохгүй. Аль аль нь өөрийн Maps холбоосыг авна.
     */
    @Transactional
    public JoinResult join(String token, Long userId, JoinRequest req) {
        Session s = groups.findByInviteToken(token).filter(x -> x.isInviteValid(Instant.now()))
                .orElseThrow(GroupPlanningService::invalidInvite);
        PlaceRef place = place(s.meetingPlaceId).orElseThrow(GroupPlanningService::invalidInvite);
        LatLng dest = new LatLng(place.lat(), place.lng());

        Optional<Member> existing = userId == null ? Optional.empty() : s.memberOfUser(userId);
        Member m;
        if (existing.isPresent()) {
            m = existing.get();
            if (req.lat() != null && req.lng() != null) {
                groups.updateMemberStart(m.id, req.lat(), req.lng(), StartSource.PINNED,
                        req.transportMode() != null ? req.transportMode() : m.transportMode);
            }
        } else {
            if (s.members.size() >= MAX_MEMBERS) {
                throw new ApiException(HttpStatus.CONFLICT, "GROUP_FULL", "Бүлэг дүүрсэн байна");
            }
            m = new Member();
            m.role = MemberRole.MEMBER;
            m.joinedViaLink = true;
            if (userId != null) {
                PreferencesDto prefs = auth.preferences(userId);
                m.userId = userId;
                m.transportMode = req.transportMode() != null ? req.transportMode() : firstMode(prefs);
                m.usesInterests = consents.isGranted(userId, ConsentDataType.INTERESTS);
            } else {
                if (req.guestName() == null || req.guestName().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "Нэрээ оруулна уу");
                }
                m.guestName = req.guestName().strip();
                m.transportMode = req.transportMode() != null ? req.transportMode() : TransportMode.WALKING;
            }
            if (req.lat() != null && req.lng() != null) {
                m.startLat = req.lat();
                m.startLng = req.lng();
                m.startSource = StartSource.PINNED;
            }
            m.id = groups.insertMember(s.id, m);
        }
        long memberId = m.id;
        Member fresh = load(s.id).members.stream().filter(x -> x.id == memberId).findFirst().orElseThrow();
        Integer minutes = fresh.hasStart() ? travel(fresh, dest) : null;
        groups.updateTravel(fresh.id, minutes);
        Long routeId = null;
        if (fresh.userId != null && fresh.hasStart()) {
            routeId = myRoute(s.id, fresh.userId);
            if (routeId == null) {
                routeId = routing.createGroupRoute(fresh.userId, new LatLng(fresh.startLat, fresh.startLng),
                        fresh.transportMode, s.timeBudgetMinutes, place.id(), s.id);
            }
        }
        return new JoinResult(fresh.id, fresh.displayName, place, links.linkFor(fresh, dest), minutes, routeId);
    }

    /** Өнгөрсөн өдрүүдийн SHARED төлөвлөгөө COMPLETED, нэг өдрөөс хуучин хуваалцаагүй нь CANCELLED болно. */
    @Scheduled(cron = "0 20 * * * *", zone = "Asia/Ulaanbaatar")
    public int closeExpired() {
        return groups.closeExpired();
    }

    // ---------------------------------------------------------------------------------------------

    private void addFriend(long sessionId, long friendId, Double pinLat, Double pinLng, TransportMode mode,
                           double initiatorLat, double initiatorLng) {
        MemberStartResolver.Start start = starts.resolve(friendId, pinLat, pinLng, initiatorLat, initiatorLng);
        Member m = new Member();
        m.userId = friendId;
        m.role = MemberRole.MEMBER;
        m.startLat = start.lat();
        m.startLng = start.lng();
        m.startSource = start.source();
        m.transportMode = mode != null ? mode : firstMode(auth.preferences(friendId));
        m.usesInterests = consents.isGranted(friendId, ConsentDataType.INTERESTS);
        groups.insertMember(sessionId, m);
    }

    private GroupView view(long viewerId, Session s) {
        boolean isInitiator = s.initiator().userId != null && s.initiator().userId == viewerId;
        PlaceRef place = s.meetingPlaceId == null ? null : place(s.meetingPlaceId).orElse(null);
        LatLng dest = place == null ? null : new LatLng(place.lat(), place.lng());
        List<MemberView> members = new ArrayList<>();
        for (Member m : s.members) {
            boolean me = m.userId != null && m.userId == viewerId;
            boolean showStart = me || (isInitiator && !m.isGuest());
            String url = dest != null && (me || isInitiator) ? links.linkFor(m, dest) : null;
            members.add(new MemberView(m.id, m.role, m.userId, m.displayName, m.isGuest(), m.startSource,
                    showStart ? m.startLat : null, showStart ? m.startLng : null, m.transportMode, m.usesInterests,
                    m.travelMinutes, m.joinedViaLink, url, me));
        }
        boolean open = s.status == GroupStatus.SHARED;
        return new GroupView(s.id, s.status, s.timeBudgetMinutes, s.budgetLevel, s.createdAt, place, members,
                isInitiator, isInitiator && open ? s.inviteToken : null, isInitiator && open ? s.inviteExpiresAt : null,
                myRoute(s.id, viewerId));
    }

    private Integer travel(Member m, LatLng dest) {
        return travelTimes.minutesBetween(m.startLat, m.startLng, dest.lat(), dest.lng(), m.transportMode);
    }

    private Long myRoute(long sessionId, long userId) {
        return jdbc.query("SELECT id FROM routes WHERE group_session_id = ? AND owner_id = ? ORDER BY id DESC LIMIT 1",
                (rs, i) -> rs.getLong(1), sessionId, userId).stream().findFirst().orElse(null);
    }

    private Optional<PlaceRef> place(long placeId) {
        return jdbc.query("""
                SELECT p.id, p.name_cyrillic, c.name_mn, m.code, ST_Y(p.location), ST_X(p.location)
                FROM places p JOIN place_categories c ON c.id = p.category_id
                LEFT JOIN place_categories m ON m.id = c.parent_id
                WHERE p.id = ? AND p.is_active
                """, (rs, i) -> new PlaceRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getDouble(5), rs.getDouble(6)), placeId).stream().findFirst();
    }

    private Session load(long sessionId) {
        return groups.findById(sessionId).orElseThrow(GroupPlanningService::notFound);
    }

    /** Санаачлагчийн төлөвлөж буй төлөвлөгөө (гишүүд, хувилбарууд өөрчлөгдөж болно). */
    private Session ownedOpen(long userId, long sessionId) {
        Session s = load(sessionId);
        if (s.initiator().userId == null || s.initiator().userId != userId) throw notFound();
        if (s.status != GroupStatus.RECOMMENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_ALREADY_SHARED", "Хуваалцсан төлөвлөгөөг өөрчлөх боломжгүй");
        }
        return s;
    }

    private static Member member(Session s, Long memberId) {
        return s.members.stream().filter(m -> memberId != null && m.id == memberId).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Гишүүн олдсонгүй"));
    }

    private static TransportMode firstMode(PreferencesDto prefs) {
        return prefs.transportModes().stream().sorted().findFirst().orElse(TransportMode.WALKING);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Бүлгийн төлөвлөгөө олдсонгүй");
    }

    private static ApiException invalidInvite() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITE_INVALID", "Урилгын холбоос хүчингүй эсвэл хугацаа нь дууссан");
    }

    /** ХШ08: хэрэглэгч бүх бүлгээс гарна; өөрийн эхлүүлсэн төлөвлөгөөнүүд цуцлагдана. */
    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("""
                UPDATE group_sessions SET status = 'CANCELLED', invite_token = NULL, invite_expires_at = NULL, closed_at = now()
                WHERE status IN ('RECOMMENDED', 'SHARED')
                  AND id IN (SELECT session_id FROM group_members WHERE user_id = ? AND role = 'INITIATOR')
                """, e.userId());
        jdbc.update("""
                UPDATE group_members SET user_id = NULL, guest_name = 'Устгасан хэрэглэгч', start_location = NULL,
                                         start_source = NULL
                WHERE user_id = ?
                """, e.userId());
    }

}
