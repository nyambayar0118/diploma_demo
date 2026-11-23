package mn.diploma.routeplanner.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * СНШ06: бүрэлдэхүүн хэсэг ажиллахгүй болох, API зорилтот хугацаанаас удаан болох эсвэл серверийн алдаа их
 * гарахад администраторт имэйл илгээнэ. Нэг асуудлын талаар {@link #COOLDOWN} хугацаанд нэгээс илүү имэйл
 * илгээхгүй; SMTP тохиргоо (app.alert.smtp.host) байхгүй бол анхааруулгыг зөвхөн бүртгэлд бичнэ.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    static final Duration COOLDOWN = Duration.ofMinutes(30);
    static final Duration RECENT = Duration.ofMinutes(5);
    static final int MIN_REQUESTS = 20;
    static final double MAX_ERROR_RATE = 0.05;

    private final MonitoringService monitoring;
    private final RequestMetrics metrics;
    private final JavaMailSender mail;
    private final String to;
    private final String from;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();
    private int consecutiveDown;

    public AlertService(MonitoringService monitoring, RequestMetrics metrics,
                        @Value("${app.alert.email:}") String to,
                        @Value("${app.alert.smtp.host:}") String host,
                        @Value("${app.alert.smtp.port:587}") int port,
                        @Value("${app.alert.smtp.username:}") String username,
                        @Value("${app.alert.smtp.password:}") String password) {
        this.monitoring = monitoring;
        this.metrics = metrics;
        this.to = to;
        this.from = username;
        if (host.isBlank()) {
            this.mail = null;
        } else {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            sender.setUsername(username.isBlank() ? null : username);
            sender.setPassword(password.isBlank() ? null : password);
            sender.getJavaMailProperties().put("mail.smtp.auth", String.valueOf(!username.isBlank()));
            sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
            sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "5000");
            sender.getJavaMailProperties().put("mail.smtp.timeout", "5000");
            this.mail = sender;
        }
    }

    public boolean mailConfigured() {
        return mail != null && !to.isBlank();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void evaluate() {
        List<String[]> problems = new ArrayList<>();
        MonitoringService.HealthReport health = monitoring.lastOrCheck();
        consecutiveDown = health.up() ? 0 : consecutiveDown + 1;
        for (MonitoringService.ComponentStatus c : health.components()) {
            if (!c.up() && (!"database".equals(c.name()) || consecutiveDown >= 2)) {
                problems.add(new String[] {"down:" + c.name(), c.name() + " ажиллахгүй байна (" + c.detail() + ")"});
            }
        }
        int requests = 0;
        int serverErrors = 0;
        for (RequestMetrics.EndpointStats s : metrics.stats(RECENT)) {
            requests += s.count();
            serverErrors += s.serverErrors();
            if (!s.meetsTarget() && s.count() >= 5) {
                problems.add(new String[] {"slow:" + s.endpoint(), "%s удаан: p95 %d мс (зорилт %d мс, %d хүсэлт)"
                        .formatted(s.endpoint(), s.p95Ms(), s.targetP95Ms(), s.count())});
            }
        }
        if (requests >= MIN_REQUESTS && serverErrors > MAX_ERROR_RATE * requests) {
            problems.add(new String[] {"errors", "Сүүлийн 5 минутад серверийн алдаа %d/%d (%.0f%%)"
                    .formatted(serverErrors, requests, 100.0 * serverErrors / requests)});
        }
        problems.forEach(p -> alert(p[0], p[1]));
    }

    void alert(String key, String message) {
        Instant prev = lastSent.get(key);
        if (prev != null && prev.isAfter(Instant.now().minus(COOLDOWN))) return;
        lastSent.put(key, Instant.now());
        log.warn("ALERT {}", message);
        if (!mailConfigured()) return;
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setTo(to.split(","));
            if (!from.isBlank()) m.setFrom(from);
            m.setSubject("[Ухаалаг маршрут] " + message);
            m.setText(message + "\n\nХяналтын хуудас: /admin\nЦаг: " + Instant.now());
            mail.send(m);
        } catch (Exception e) {
            log.error("Alert e-mail failed: {}", e.getMessage());
        }
    }
}
