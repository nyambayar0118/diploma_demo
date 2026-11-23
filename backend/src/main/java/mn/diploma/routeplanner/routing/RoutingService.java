package mn.diploma.routeplanner.routing;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.AuthDtos.PreferencesDto;
import mn.diploma.routeplanner.auth.AuthService;
import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.common.TransportMode;
import mn.diploma.routeplanner.interaction.InteractionService;
import mn.diploma.routeplanner.interaction.InteractionType;
import mn.diploma.routeplanner.place.OpeningHours;
import mn.diploma.routeplanner.recommendation.RecommendationService;
import mn.diploma.routeplanner.routing.RouteStop.PlaceInfo;
import mn.diploma.routeplanner.routing.RoutingDtos.CreateRouteRequest;
import mn.diploma.routeplanner.routing.RoutingDtos.ModifyStopsRequest;
import mn.diploma.routeplanner.routing.RoutingDtos.RouteView;
import mn.diploma.routeplanner.routing.RoutingDtos.StopRequest;
import mn.diploma.routeplanner.routing.RoutingDtos.StopView;

/**
 * Маршрутын модулийн нийтийн сервис (АШ15, АШ22, ДШ05, ХСШ05-07): 1-4 газраар маршрут үүсгэж, зогсоолуудыг
 * дараалуулж, ирэх цагийг тооцоод маршрутыг Google Maps руу шилжүүлнэ.
 */
@Service
public class RoutingService {

    public static final int MAX_STOPS = 4;
    /** Автомат маршрутын зогсоолын тоо (ХСШ07: анхдагч 2-3). */
    static final int AUTO_STOPS = 3;
    static final int AUTO_CANDIDATES = 12;
    static final int LIST_LIMIT = 20;

    private final RouteRepository routes;
    private final TravelTimeEstimator travelTimes;
    private final RouteOrderOptimizer optimizer;
    private final MapsUrlBuilder mapsUrls;
    private final StopSelector stopSelector;
    private final RecommendationService recommendations;
    private final InteractionService interactions;
    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public RoutingService(RouteRepository routes, TravelTimeEstimator travelTimes, RouteOrderOptimizer optimizer,
                          MapsUrlBuilder mapsUrls, StopSelector stopSelector, RecommendationService recommendations,
                          InteractionService interactions, AuthService auth, JdbcTemplate jdbc) {
        this.routes = routes;
        this.travelTimes = travelTimes;
        this.optimizer = optimizer;
        this.mapsUrls = mapsUrls;
        this.stopSelector = stopSelector;
        this.recommendations = recommendations;
        this.interactions = interactions;
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @Transactional
    public RouteView createRoute(long userId, CreateRouteRequest req) {
        PreferencesDto prefs = auth.preferences(userId);
        TransportMode mode = req.transportMode() != null ? req.transportMode()
                : prefs.transportModes().stream().sorted().findFirst().orElse(TransportMode.WALKING);
        int budget = req.timeBudgetMinutes() != null ? req.timeBudgetMinutes() : prefs.timeBudgetMin();
        LocalDateTime startAt = (req.startAt() != null ? req.startAt() : LocalDateTime.now(RecommendationService.ZONE))
                .truncatedTo(ChronoUnit.MINUTES);
        LatLng start = new LatLng(req.lat(), req.lng());

        List<StopRequest> requested = req.stops() == null || req.stops().isEmpty()
                ? autoSelect(userId, start, mode, budget, startAt, req.categories())
                : req.stops();
        Set<Long> distinct = new HashSet<>();
        for (StopRequest s : requested) {
            if (!distinct.add(s.placeId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_STOP", "Нэг газрыг хоёр удаа нэмсэн байна");
            }
        }

        Route route = new Route(userId, start, mode, budget, startAt);
        Map<Long, PlaceInfo> places = routes.activePlaces(distinct);
        for (StopRequest s : requested) {
            route.stops.add(newStop(userId, places, s.placeId(), s.recommendationId()));
        }
        rebuild(route);
        routes.insert(route);
        // Санал болгосон газрыг маршрутад сонгох нь илэрхий хүлээн авалт (сургалтын шошго 1)
        for (RouteStop s : route.stops) {
            if (s.recommendationId != null) {
                interactions.log(userId, s.place.id(), InteractionType.ACCEPT, s.recommendationId, route.id, null, null);
            }
        }
        return view(route);
    }

    /** Бүлгийн төлөвлөгөө хуваалцсан (АШ24): бүртгэлтэй гишүүн бүр уулзах газар хүртэл нэг зогсоолтой маршрут авна. */
    @Transactional
    public long createGroupRoute(long ownerId, LatLng start, TransportMode mode, int budget, long placeId,
                                 long groupSessionId) {
        LocalDateTime startAt = LocalDateTime.now(RecommendationService.ZONE).truncatedTo(ChronoUnit.MINUTES);
        Route route = new Route(ownerId, start, mode, budget, startAt);
        route.groupSessionId = groupSessionId;
        route.stops.add(newStop(ownerId, routes.activePlaces(List.of(placeId)), placeId, null));
        rebuild(route);
        routes.insert(route);
        return route.id;
    }

    /** Бүлгийн төлөвлөгөө цуцлагдсан: гишүүдийн нээлттэй маршрутууд мөн цуцлагдана. */
    @Transactional
    public void cancelGroupRoutes(long groupSessionId) {
        jdbc.update("UPDATE routes SET status = 'CANCELLED', closed_at = now() "
                + "WHERE group_session_id = ? AND status IN ('CREATED', 'OPENED_IN_MAPS')", groupSessionId);
    }

    @Transactional(readOnly = true)
    public RouteView get(long userId, long routeId) {
        return view(owned(userId, routeId));
    }

    @Transactional(readOnly = true)
    public List<RouteView> list(long userId) {
        return routes.findByOwner(userId, LIST_LIMIT).stream().map(this::view).toList();
    }

    @Transactional
    public RouteView modifyStops(long userId, long routeId, ModifyStopsRequest req) {
        Route route = owned(userId, routeId);
        if (!route.canModify()) {
            throw new ApiException(HttpStatus.CONFLICT, "ROUTE_LOCKED", "Google Maps-ээр нээсэн маршрутыг өөрчлөх боломжгүй");
        }
        List<Long> changedPlaces = new ArrayList<>();
        // Хэрэглэгчийн өөрөө дараалуулсан маршрут зогсоол нэмэх, хасахад тэр дараалалаа хадгална
        boolean optimize = isShortestOrder(route);
        switch (req.op()) {
            case ADD -> {
                if (route.stops.size() >= MAX_STOPS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_STOPS",
                            "Маршрут хамгийн ихдээ " + MAX_STOPS + " зогсоолтой");
                }
                RouteStop added = newStop(userId, requirePlace(route, req.placeId()), req.placeId(), req.recommendationId());
                route.stops.add(added);
                changedPlaces.add(added.place.id());
            }
            case REMOVE -> {
                RouteStop removed = requireStop(route, req.stopId());
                if (route.stops.size() == 1) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_STOP",
                            "Сүүлийн зогсоолыг хасах боломжгүй. Маршрутыг цуцлана уу.");
                }
                route.stops.remove(removed);
                changedPlaces.add(removed.place.id());
            }
            case REPLACE -> {
                RouteStop old = requireStop(route, req.stopId());
                RouteStop added = newStop(userId, requirePlace(route, req.placeId()), req.placeId(), req.recommendationId());
                route.stops.set(route.stops.indexOf(old), added);
                changedPlaces.add(old.place.id());
                changedPlaces.add(added.place.id());
            }
            case REORDER -> {
                List<Long> ids = req.stopIds() == null ? List.of() : req.stopIds();
                if (ids.size() != route.stops.size() || ids.stream().distinct().count() != ids.size()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER", "Бүх зогсоолыг нэг удаа дурдана уу");
                }
                List<RouteStop> reordered = new ArrayList<>();
                for (Long id : ids) reordered.add(requireStop(route, id));
                route.stops = reordered;
                optimize = false;
            }
            case OPTIMIZE -> optimize = true;
        }
        rebuild(route, optimize);
        routes.replaceStops(route);
        routes.update(route);
        changedPlaces.forEach(p -> interactions.log(userId, p, InteractionType.ROUTE_MODIFY, null, route.id, null, null));
        return view(route);
    }

    /** CREATED -> OPENED_IN_MAPS; холбоосууд буцаах хариунд багтана (АШ22). */
    @Transactional
    public RouteView openInMaps(long userId, long routeId) {
        Route route = owned(userId, routeId);
        if (route.status == RouteStatus.CREATED) {
            route.status = RouteStatus.OPENED_IN_MAPS;
            route.openedAt = Instant.now();
            routes.update(route);
            for (RouteStop s : route.stops) {
                interactions.log(userId, s.place.id(), InteractionType.OPEN_IN_MAPS, s.recommendationId, route.id,
                        null, null);
            }
        } else if (route.status != RouteStatus.OPENED_IN_MAPS) {
            throw notOpen();
        }
        return view(route);
    }

    @Transactional
    public RouteView complete(long userId, long routeId) {
        Route route = owned(userId, routeId);
        if (route.status != RouteStatus.OPENED_IN_MAPS) {
            throw new ApiException(HttpStatus.CONFLICT, "ROUTE_NOT_OPENED", "Маршрутыг эхлээд Google Maps-ээр нээнэ үү");
        }
        close(route, RouteStatus.COMPLETED);
        return view(route);
    }

    @Transactional
    public void cancel(long userId, long routeId) {
        Route route = owned(userId, routeId);
        if (!route.status.isOpen()) throw notOpen();
        close(route, RouteStatus.CANCELLED);
    }

    void close(Route route, RouteStatus status) {
        route.status = status;
        route.closedAt = Instant.now();
        routes.update(route);
    }

    /** Зогсоолуудыг хамгийн бага зорчих хугацаагаар дараалуулж (ДШ05), ирэх цаг болон нийт хугацааг дахин тооцно. */
    void rebuild(Route route) {
        rebuild(route, true);
    }

    /** {@code optimize = false} бол одоогийн дарааллыг хадгална (хэрэглэгч гараар дараалуулсан). */
    void rebuild(Route route, boolean optimize) {
        List<LatLng> points = new ArrayList<>();
        points.add(route.start);
        route.stops.forEach(s -> points.add(s.location()));
        TravelTimeMatrix matrix = travelTimes.estimate(points, route.transportMode);
        List<Integer> order = new ArrayList<>();
        if (optimize) {
            order = optimizer.optimizeOrder(matrix, 0);
        } else {
            for (int i = 1; i <= route.stops.size(); i++) order.add(i);
        }

        List<RouteStop> ordered = new ArrayList<>();
        LocalDateTime t = route.plannedStart;
        int prev = 0;
        for (int idx : order) {
            RouteStop s = route.stops.get(idx - 1);
            t = t.plusMinutes(matrix.between(prev, idx));
            s.stopOrder = ordered.size() + 1;
            s.estimatedArrival = t;
            t = t.plusMinutes(s.stayMinutes);
            ordered.add(s);
            prev = idx;
        }
        route.stops = ordered;
        route.estimatedDurationMinutes = (int) Duration.between(route.plannedStart, t).toMinutes();
    }

    /** Зогсоолууд одоо хамгийн богино дарааллаар байгаа эсэх (гараар дараалуулсны дараа false). */
    boolean isShortestOrder(Route route) {
        if (route.stops.size() < 2) return true;
        List<RouteStop> current = route.stops.stream().sorted(Comparator.comparingInt(s -> s.stopOrder)).toList();
        List<LatLng> points = new ArrayList<>();
        points.add(route.start);
        current.forEach(s -> points.add(s.location()));
        TravelTimeMatrix matrix = travelTimes.estimate(points, route.transportMode);
        List<Integer> asIs = new ArrayList<>();
        for (int i = 1; i <= current.size(); i++) asIs.add(i);
        return RouteOrderOptimizer.travelMinutes(matrix, 0, asIs)
                <= RouteOrderOptimizer.travelMinutes(matrix, 0, optimizer.optimizeOrder(matrix, 0));
    }

    RouteView view(Route route) {
        List<StopView> stops = new ArrayList<>();
        List<RouteStop> sorted = route.stops.stream().sorted(Comparator.comparingInt(s -> s.stopOrder)).toList();
        LatLng prev = route.start;
        for (RouteStop s : sorted) {
            PlaceInfo p = s.place;
            int travel = travelTimes.minutesBetween(prev.lat(), prev.lng(), p.lat(), p.lng(), route.transportMode);
            stops.add(new StopView(s.id, s.stopOrder, p.id(), p.name(), p.nameLatin(), p.category(), p.categoryName(),
                    p.mainCategory(), p.lat(), p.lng(), travel, s.estimatedArrival, s.stayMinutes,
                    OpeningHours.statusAt(p.openingHours(), s.estimatedArrival), s.visited, s.skippedAt != null));
            prev = s.location();
        }
        List<MapsLink> links = mapsUrls.directions(route.start, sorted.stream().map(RouteStop::location).toList(),
                route.transportMode);
        Boolean feedback = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM satisfaction_feedbacks WHERE route_id = ? AND user_id = ?)",
                Boolean.class, route.id, route.ownerId);
        return new RouteView(route.id, route.status, route.transportMode, route.start.lat(), route.start.lng(),
                route.plannedStart, route.timeBudgetMinutes, route.estimatedDurationMinutes,
                route.estimatedDurationMinutes > route.timeBudgetMinutes, route.createdAt, stops, links,
                Boolean.TRUE.equals(feedback), route.groupSessionId, isShortestOrder(route));
    }

    private List<StopRequest> autoSelect(long userId, LatLng start, TransportMode mode, int budget, LocalDateTime at,
                                         List<String> categories) {
        RecommendationService.Batch batch = recommendations.recommend(userId, new RecommendationService.Request(
                start.lat(), start.lng(), budget, null, mode, AUTO_CANDIDATES, at, categories));
        List<StopSelector.ScoredPlace> candidates = batch.items().stream()
                .map(i -> new StopSelector.ScoredPlace(i.placeId(), i.recommendationId(), i.category(), i.lat(), i.lng()))
                .toList();
        List<StopSelector.ScoredPlace> chosen = stopSelector.select(start, candidates, mode, budget, AUTO_STOPS);
        if (chosen.isEmpty()) {
            throw new ApiException(HttpStatus.valueOf(422), "NO_PLACES",
                    "Тохирох газар олдсонгүй. Хугацаа эсвэл тээврийн хэрэгслээ өөрчилж үзнэ үү.");
        }
        return chosen.stream().map(c -> new StopRequest(c.placeId(), c.recommendationId())).toList();
    }

    private RouteStop newStop(long userId, Map<Long, PlaceInfo> places, long placeId, Long recommendationId) {
        PlaceInfo place = places.get(placeId);
        if (place == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Газар олдсонгүй: " + placeId);
        }
        return new RouteStop(place, routes.ownRecommendation(userId, recommendationId, placeId),
                StayDurations.of(place.category()));
    }

    private Map<Long, PlaceInfo> requirePlace(Route route, Long placeId) {
        if (placeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLACE_REQUIRED", "placeId шаардлагатай");
        }
        if (route.stops.stream().anyMatch(s -> s.place.id() == placeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_STOP", "Энэ газар маршрутад аль хэдийн байна");
        }
        return routes.activePlaces(List.of(placeId));
    }

    private static RouteStop requireStop(Route route, Long stopId) {
        return route.stops.stream().filter(s -> s.id.equals(stopId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STOP_NOT_FOUND", "Зогсоол олдсонгүй"));
    }

    /** Маршрут байгаа бөгөөд хэрэглэгчийнх бол түүнийг; үгүй бол 404 (бусдын маршрутыг ил гаргахгүй). */
    Route owned(long userId, long routeId) {
        return routes.findById(routeId).filter(r -> r.ownerId == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Маршрут олдсонгүй"));
    }

    private static ApiException notOpen() {
        return new ApiException(HttpStatus.CONFLICT, "ROUTE_CLOSED", "Маршрут дууссан эсвэл цуцлагдсан байна");
    }
}
