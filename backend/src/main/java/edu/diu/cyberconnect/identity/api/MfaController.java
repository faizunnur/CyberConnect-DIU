package edu.diu.cyberconnect.identity.api;

import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.identity.application.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final MfaService mfa;

    public MfaController(MfaService mfa) {
        this.mfa = mfa;
    }

    @PostMapping("/enrol")
    public MfaService.Enrolment enrol(HttpServletRequest http) {
        return mfa.beginEnrolment(CurrentUser.require().getId(), http.getSession(true));
    }

    @PostMapping("/enrol/confirm")
    public MfaService.RecoveryCodes confirm(@Valid @RequestBody CodeRequest request, HttpServletRequest http) {
        return mfa.confirmEnrolment(CurrentUser.require().getId(), request.code(), http.getSession(true));
    }

    @PostMapping("/step-up")
    public StepUpResponse stepUp(@Valid @RequestBody CodeRequest request, HttpServletRequest http) {
        return new StepUpResponse(mfa.stepUp(CurrentUser.require().getId(), request.code(), http.getSession(true)));
    }

    @PostMapping("/recovery-codes/regenerate")
    public MfaService.RecoveryCodes regenerate(@Valid @RequestBody CodeRequest request) {
        return mfa.regenerateRecoveryCodes(CurrentUser.require().getId(), request.code());
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Valid @RequestBody CodeRequest request) {
        mfa.disable(CurrentUser.require().getId(), request.code());
    }

    public record CodeRequest(@NotBlank @Size(max = 16) String code) {
    }

    public record StepUpResponse(Instant validUntil) {
    }
}
