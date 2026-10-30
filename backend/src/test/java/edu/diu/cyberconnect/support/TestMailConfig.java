package edu.diu.cyberconnect.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the SMTP sender with an in-memory capture; the single @Primary bean satisfies both injection types. */
@TestConfiguration
public class TestMailConfig {

    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
        return new CapturingMailSender();
    }
}
