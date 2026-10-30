package edu.diu.cyberconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@ConfigurationPropertiesScan
public class CyberConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(CyberConnectApplication.class, args);
    }
}
