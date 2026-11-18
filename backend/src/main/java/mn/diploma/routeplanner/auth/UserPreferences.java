package mn.diploma.routeplanner.auth;

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;

/** Defaults come from the user survey: 5 km radius, 2–4 hours, 2–3 stops (ХСШ07). */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Convert(converter = TransportModesConverter.class)
    @Column(name = "transport_modes", nullable = false)
    private Set<TransportMode> transportModes = EnumSet.of(TransportMode.WALKING, TransportMode.PUBLIC_TRANSPORT);

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_level", nullable = false)
    private PriceLevel budgetLevel = PriceLevel.MEDIUM;

    @Column(name = "search_radius_m", nullable = false)
    private int searchRadiusM = 5000;

    @Column(name = "time_budget_min", nullable = false)
    private int timeBudgetMin = 180;

    @Column(name = "stop_count", nullable = false)
    private int stopCount = 3;

    protected UserPreferences() {
    }

    public static UserPreferences defaultsFor(Long userId) {
        UserPreferences p = new UserPreferences();
        p.userId = userId;
        return p;
    }

    public void update(Set<TransportMode> transportModes, PriceLevel budgetLevel, int searchRadiusM,
                       int timeBudgetMin, int stopCount) {
        this.transportModes = EnumSet.copyOf(transportModes);
        this.budgetLevel = budgetLevel;
        this.searchRadiusM = searchRadiusM;
        this.timeBudgetMin = timeBudgetMin;
        this.stopCount = stopCount;
    }

    public Long getUserId() { return userId; }
    public Set<TransportMode> getTransportModes() { return transportModes; }
    public PriceLevel getBudgetLevel() { return budgetLevel; }
    public int getSearchRadiusM() { return searchRadiusM; }
    public int getTimeBudgetMin() { return timeBudgetMin; }
    public int getStopCount() { return stopCount; }
}
