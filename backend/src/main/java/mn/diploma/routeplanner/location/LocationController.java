package mn.diploma.routeplanner.location;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.location.LocationService.FriendLocation;
import mn.diploma.routeplanner.location.LocationService.MyLocation;

/** Location module API. */
@RestController
public class LocationController {

    public record LocationUpdate(@DecimalMin("-90") @DecimalMax("90") Double lat,
                                 @DecimalMin("-180") @DecimalMax("180") Double lng,
                                 @NotNull Boolean shared) {
    }

    private final LocationService locations;

    public LocationController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping("/api/locations/me")
    public MyLocation mine(@AuthenticationPrincipal Jwt jwt) {
        return locations.mine(JwtService.userId(jwt));
    }

    @PutMapping("/api/locations/me")
    public MyLocation update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LocationUpdate req) {
        return locations.update(JwtService.userId(jwt), req.lat(), req.lng(), req.shared());
    }

    @GetMapping("/api/friends/locations")
    public List<FriendLocation> friendLocations(@AuthenticationPrincipal Jwt jwt) {
        return locations.friendLocations(JwtService.userId(jwt));
    }
}
