package mn.diploma.routeplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Системийн үндсэн сервер (зохиомжийн бүлэг 3.1): модульчлагдсан монолит.
 * {@code mn.diploma.routeplanner} доорх дээд түвшний багц бүр нэг модуль болно
 * (auth, friend, location, interaction, interest, place, contribution,
 * recommendation, routing, group, monitoring). Модулиуд бие биеэ зөвхөн
 * нийтэд нээлттэй сервисийн интерфейсээр дамжуулан дуудна.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RoutePlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutePlannerApplication.class, args);
    }
}
