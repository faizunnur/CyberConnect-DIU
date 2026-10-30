package edu.diu.cyberconnect.common.ratelimit;

import edu.diu.cyberconnect.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

    public RateLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many attempts. Please wait and try again.");
    }
}
