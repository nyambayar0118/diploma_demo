package mn.diploma.routeplanner.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Closes routes whose day has passed (state diagram: "маршрутын өдөр өнгөрөхөд"). Runs every hour. */
@Component
public class RouteExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RouteExpiryJob.class);

    private final RouteRepository routes;

    public RouteExpiryJob(RouteRepository routes) {
        this.routes = routes;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Ulaanbaatar")
    public int closeExpiredRoutes() {
        int closed = routes.closeExpired(VisitService.now());
        if (closed > 0) log.info("Closed {} expired routes", closed);
        return closed;
    }
}
