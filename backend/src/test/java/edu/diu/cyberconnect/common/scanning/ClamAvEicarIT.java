package edu.diu.cyberconnect.common.scanning;

import static org.assertj.core.api.Assertions.assertThat;

import edu.diu.cyberconnect.common.config.ScannerProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Real ClamAV check with the EICAR test string (a harmless industry-standard test signature). Opt-in because the
 * image needs ~1.5 GB RAM and a signature download: run with CLAMAV_IT=true mvn verify.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CLAMAV_IT", matches = "true")
@SuppressWarnings({"rawtypes", "unchecked", "resource"})
class ClamAvEicarIT {

    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Test
    void detectsEicarAndAcceptsCleanContent() {
        GenericContainer clamav = new GenericContainer("clamav/clamav:1.4.6")
                .withExposedPorts(3310)
                .withStartupTimeout(Duration.ofMinutes(10))
                .waitingFor(Wait.forLogMessage(".*socket found, clamd started.*", 1));
        clamav.start();
        try {
            ClamAvMalwareScanner scanner = new ClamAvMalwareScanner(new ScannerProperties("clamav",
                    new ScannerProperties.ClamAv(clamav.getHost(), clamav.getMappedPort(3310), 60_000)));

            byte[] eicar = EICAR.getBytes(StandardCharsets.US_ASCII);
            ScanResult infected = scanner.scan(new ByteArrayInputStream(eicar), eicar.length);
            assertThat(infected.status()).isEqualTo(ScanResult.Status.INFECTED);
            assertThat(infected.signature()).containsIgnoringCase("eicar");

            byte[] clean = "just a fictional id card".getBytes(StandardCharsets.US_ASCII);
            assertThat(scanner.scan(new ByteArrayInputStream(clean), clean.length).isClean()).isTrue();
        } finally {
            clamav.stop();
        }
    }
}
