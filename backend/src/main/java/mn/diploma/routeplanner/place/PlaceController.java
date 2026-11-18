package mn.diploma.routeplanner.place;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Place module API (design table "Газрын модулийн API").
 * Open to everyone, including users who are not logged in.
 */
@RestController
public class PlaceController {

    private static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_LIMIT = 5000;

    private final PlaceQueryRepository places;

    public PlaceController(PlaceQueryRepository places) {
        this.places = places;
    }

    /**
     * GET /api/places?bbox=minLon,minLat,maxLon,maxLat[&limit=N]
     * Places inside the visible map area as a GeoJSON FeatureCollection (АШ08, ХХШ02).
     */
    @GetMapping(value = "/api/places", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> placesInBbox(@RequestParam String bbox,
                                               @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        double[] b = parseBbox(bbox);
        int safeLimit = Math.clamp(limit, 1, MAX_LIMIT);
        return ResponseEntity.ok(places.placesInBboxAsGeoJson(b[0], b[1], b[2], b[3], safeLimit));
    }

    /** GET /api/categories: main categories with their subcategories (ДШ11). */
    @GetMapping(value = "/api/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> categories() {
        return ResponseEntity.ok(places.categoryTreeAsJson());
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
