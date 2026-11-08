package edu.diu.cyberconnect.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Calendar rules (opportunity deadlines, "today") follow the institution's time zone, not the server's: a deadline of
 * 6 November ends at midnight in Dhaka even though containers run in UTC. Instants stay UTC everywhere.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Dhaka}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
