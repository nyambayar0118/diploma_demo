package mn.diploma.routeplanner.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

/**
 * API тус бүрийн сүүлийн нэг цагийн хариу өгөх хугацаа, алдааг санах ойд хадгална (СНШ05). Минут тутмын
 * хэсгүүдэд хуваана; туршилтын ачаалал бага тул хугацааг бүхэлд нь хадгалж, перцентилийг яг тооцно.
 */
@Component
public class RequestMetrics {

    static final Duration WINDOW = Duration.ofMinutes(60);
    private static final int MAX_PER_BUCKET = 5000;

    /** Хариу өгөх хугацааны зорилт (СГШ01-СГШ03), p95, миллисекундээр. */
    static final Map<String, Long> TARGETS_MS = Map.of(
            "GET /api/places/search", 1000L,
            "POST /api/recommendations", 2000L,
            "POST /api/routes", 1000L);

    private record Sample(long millis, int status) {
    }

    private static final class Bucket {
        final long minute;
        final ConcurrentLinkedDeque<Sample> samples = new ConcurrentLinkedDeque<>();

        Bucket(long minute) {
            this.minute = minute;
        }
    }

    public record EndpointStats(String endpoint, int count, int clientErrors, int serverErrors, long p50Ms, long p95Ms,
                                long maxMs, Long targetP95Ms) {
        public boolean meetsTarget() {
            return targetP95Ms == null || count == 0 || p95Ms <= targetP95Ms;
        }
    }

    private final Map<String, ConcurrentLinkedDeque<Bucket>> byEndpoint = new ConcurrentHashMap<>();
    private final Instant startedAt = Instant.now();

    public void record(String endpoint, long millis, int status) {
        long minute = Instant.now().getEpochSecond() / 60;
        ConcurrentLinkedDeque<Bucket> buckets = byEndpoint.computeIfAbsent(endpoint, k -> new ConcurrentLinkedDeque<>());
        Bucket last = buckets.peekLast();
        if (last == null || last.minute != minute) {
            synchronized (buckets) {
                last = buckets.peekLast();
                if (last == null || last.minute != minute) {
                    last = new Bucket(minute);
                    buckets.addLast(last);
                }
                long oldest = minute - WINDOW.toMinutes();
                while (!buckets.isEmpty() && buckets.peekFirst().minute <= oldest) buckets.pollFirst();
            }
        }
        if (last.samples.size() < MAX_PER_BUCKET) last.samples.add(new Sample(millis, status));
    }

    /** Сүүлийн {@code window} хугацааны үзүүлэлт, хамгийн их хүсэлттэй API эхэндээ. */
    public List<EndpointStats> stats(Duration window) {
        long from = Instant.now().getEpochSecond() / 60 - window.toMinutes();
        List<EndpointStats> result = new ArrayList<>();
        Map<String, ConcurrentLinkedDeque<Bucket>> sorted = new TreeMap<>(byEndpoint);
        sorted.forEach((endpoint, buckets) -> {
            List<Long> durations = new ArrayList<>();
            int client = 0;
            int server = 0;
            for (Bucket b : buckets) {
                if (b.minute <= from) continue;
                for (Sample s : b.samples) {
                    durations.add(s.millis());
                    if (s.status() >= 500) server++;
                    else if (s.status() >= 400) client++;
                }
            }
            if (durations.isEmpty() && !TARGETS_MS.containsKey(endpoint)) return;
            Collections.sort(durations);
            result.add(new EndpointStats(endpoint, durations.size(), client, server, percentile(durations, 50),
                    percentile(durations, 95), durations.isEmpty() ? 0 : durations.getLast(), TARGETS_MS.get(endpoint)));
        });
        result.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return result;
    }

    public Instant startedAt() {
        return startedAt;
    }

    static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }
}
