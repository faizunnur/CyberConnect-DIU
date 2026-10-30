package edu.diu.cyberconnect.identity.api;

import edu.diu.cyberconnect.affiliation.application.AffiliationDetails;
import edu.diu.cyberconnect.common.model.AffiliationType;
import edu.diu.cyberconnect.common.security.CurrentUser;
import edu.diu.cyberconnect.identity.application.AuthenticationService;
import edu.diu.cyberconnect.identity.application.EmailVerificationService;
import edu.diu.cyberconnect.identity.application.LoginResult;
import edu.diu.cyberconnect.identity.application.MeAssembler;
import edu.diu.cyberconnect.identity.application.MeResponse;
import edu.diu.cyberconnect.identity.application.PasswordResetService;
import edu.diu.cyberconnect.identity.application.RegisterCommand;
import edu.diu.cyberconnect.identity.application.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final EmailVerificationService emailVerification;
    private final PasswordResetService passwordReset;
    private final MeAssembler me;

    public AuthController(RegistrationService registration, AuthenticationService authentication,
            EmailVerificationService emailVerification, PasswordResetService passwordReset, MeAssembler me) {
        this.registration = registration;
        this.authentication = authentication;
        this.emailVerification = emailVerification;
        this.passwordReset = passwordReset;
        this.me = me;
    }

    /** Uniform 202 whether or not the email was new (see RegistrationService). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        registration.register(new RegisterCommand(request.fullName(), request.email(), request.password(),
                request.affiliationType(), new AffiliationDetails(request.studentId(), request.batch(),
                        request.department(), request.graduationYear(), request.cscMemberId()),
                request.privacyAccepted()), http.getRemoteAddr());
    }

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        return authentication.login(request.email(), request.password(), http, response);
    }

    @PostMapping("/login/mfa")
    public LoginResult loginMfa(@Valid @RequestBody CodeRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        return authentication.completeMfa(request.code(), http, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http, HttpServletResponse response) {
        authentication.logout(http, response);
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest http) {
        return me.forUser(CurrentUser.require().getId(), http.getSession(false));
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        emailVerification.verify(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification() {
        emailVerification.resend(CurrentUser.require().getId());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        passwordReset.requestReset(request.email(), http.getRemoteAddr());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.reset(request.token(), request.newPassword());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordReset.changePassword(CurrentUser.require().getId(), request.currentPassword(), request.newPassword());
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotNull AffiliationType affiliationType,
            @Size(max = 32) String studentId,
            @Size(max = 16) String batch,
            @Size(max = 120) String department,
            @Min(1990) @Max(2100) Integer graduationYear,
            @Size(max = 32) String cscMemberId,
            boolean privacyAccepted) {
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
    }

    public record CodeRequest(@NotBlank @Size(max = 16) String code) {
    }

    public record TokenRequest(@NotBlank @Size(max = 128) String token) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 128) String token,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }
}
