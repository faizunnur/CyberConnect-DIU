package edu.diu.cyberconnect.common.scanning;

import edu.diu.cyberconnect.common.config.AppProperties;
import edu.diu.cyberconnect.common.config.ScannerProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Fails start-up if production would run without a real malware scanner. */
@Component
public class ScannerProductionGuard implements InitializingBean {

    private final AppProperties app;
    private final ScannerProperties scanner;

    public ScannerProductionGuard(AppProperties app, ScannerProperties scanner) {
        this.app = app;
        this.scanner = scanner;
    }

    @Override
    public void afterPropertiesSet() {
        if (app.isProduction() && scanner.isNoOp()) {
            throw new IllegalStateException(
                    "Refusing to start: scanner.type=noop is not allowed in production. Configure scanner.type=clamav.");
        }
    }
}
