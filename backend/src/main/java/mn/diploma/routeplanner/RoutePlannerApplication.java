package mn.diploma.routeplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Core server of the system (design chapter 3.1): a modular monolith.
 * Each top-level package under {@code mn.diploma.routeplanner} is one module
 * (auth, friend, location, interaction, interest, place, contribution,
 * recommendation, routing, group, monitoring). Modules call each other only
 * through their public service interfaces.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RoutePlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutePlannerApplication.class, args);
    }
}
