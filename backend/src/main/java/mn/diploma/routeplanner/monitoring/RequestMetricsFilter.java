package mn.diploma.routeplanner.monitoring;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * /api хүсэлт бүрийн хугацааг хэмжиж, маршрутын загвараар нь ("GET /api/places/{id}") бүртгэнэ. Spring Security-ээс
 * өмнө ажилладаг тул түүний татгалзсан хүсэлтүүд ч тоологдоно.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final RequestMetrics metrics;

    public RequestMetricsFilter(RequestMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        int status = 500;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            // Controller-т хүрэхээс өмнө татгалзсан хүсэлтэд (401, 403, 404) загвар байхгүй: нэг бүлэгт нэгтгэнэ
            String path = pattern != null ? pattern.toString() : status == 404 ? "(not found)" : "(rejected)";
            metrics.record(request.getMethod() + " " + path, (System.nanoTime() - start) / 1_000_000, status);
        }
    }
}
