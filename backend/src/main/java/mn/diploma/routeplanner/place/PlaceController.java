package mn.diploma.routeplanner.place;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Газрын модулийн API (зохиомжийн "Газрын модулийн API" хүснэгт).
 * Нэвтрээгүй хэрэглэгч ч ашиглах боломжтой.
 */
@RestController
public class PlaceController {

    private static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_LIMIT = 5000;
    // Сүхбаатарын талбай: клиент газрын зургийн төвөө илгээгээгүй үед зайг үүнээс тооцно
    private static final double UB_CENTER_LAT = 47.9186;
    private static final double UB_CENTER_LNG = 106.9177;

    private final PlaceQueryRepository places;

    public PlaceController(PlaceQueryRepository places) {
        this.places = places;
    }

    /**
     * GET /api/places?bbox=minLon,minLat,maxLon,maxLat[&limit=N]
     * Газрын зургийн харагдаж буй хэсэг дэх газрууд, GeoJSON FeatureCollection хэлбэрээр (АШ08, ХХШ02).
     */
    @GetMapping(value = "/api/places", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> placesInBbox(@RequestParam String bbox,
                                               @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        double[] b = parseBbox(bbox);
        int safeLimit = Math.clamp(limit, 1, MAX_LIMIT);
        return ResponseEntity.ok(places.placesInBboxAsGeoJson(b[0], b[1], b[2], b[3], safeLimit));
    }

    /**
     * GET /api/places/search?q=...[&category=mainCode][&lat=..&lng=..][&limit=N]
     * Кирилл эсвэл латин үсгээр нэрээр хайх, үсгийн алдааг тэвчинэ (АШ01, АШ02, СГШ01).
     */
    @GetMapping(value = "/api/places/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(@RequestParam String q,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) Double lat,
                                         @RequestParam(required = false) Double lng,
                                         @RequestParam(defaultValue = "20") int limit) {
        String query = q.trim();
        if (query.length() < 2) {
            return ResponseEntity.ok("[]");
        }
        double centerLat = lat != null ? lat : UB_CENTER_LAT;
        double centerLng = lng != null ? lng : UB_CENTER_LNG;
        return ResponseEntity.ok(places.search(query, blankToNull(category), centerLat, centerLng,
                Math.clamp(limit, 1, 50)));
    }

    /** GET /api/places/{id}: нэг газрын дэлгэрэнгүй мэдээлэл (ХСШ04, АШ19). */
    @GetMapping(value = "/api/places/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> details(@PathVariable long id) {
        String json = places.detailsAsJson(id);
        if (json == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Газар олдсонгүй");
        }
        return ResponseEntity.ok(json);
    }

    /** GET /api/categories: үндсэн ангиллууд, тэдгээрийн дэд ангиллын хамт (ДШ11). */
    @GetMapping(value = "/api/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> categories() {
        return ResponseEntity.ok(places.categoryTreeAsJson());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox must be minLon,minLat,maxLon,maxLat");
        }
        double[] values = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                values[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox values must be numbers");
        }
        if (values[0] >= values[2] || values[1] >= values[3]) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox min must be smaller than max");
        }
        return values;
    }
}
