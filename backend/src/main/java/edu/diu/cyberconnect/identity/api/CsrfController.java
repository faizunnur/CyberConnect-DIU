package edu.diu.cyberconnect.identity.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstraps CSRF for the browser: calling this endpoint sets the readable XSRF-TOKEN cookie. The token value is
 * intentionally not returned in the body; the frontend reads the cookie and echoes it in the named header.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public CsrfInfo csrf(CsrfToken token) {
        return new CsrfInfo(token.getHeaderName(), "XSRF-TOKEN");
    }

    public record CsrfInfo(String headerName, String cookieName) {
    }
}
