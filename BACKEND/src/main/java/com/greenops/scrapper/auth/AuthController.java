package com.greenops.scrapper.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

    private static final String SESSION_COOKIE = "greenops_session";
    private final EmailAuthService authService;
    private final boolean secureCookie;

    public AuthController(
            EmailAuthService authService,
            @Value("${greenops.auth.cookie-secure:false}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegistrationRequest request) {
        authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.accepted().body(new MessageResponse("Verification code sent to your email."));
    }

    @PostMapping("/login")
    public ResponseEntity<MessageResponse> login(@Valid @RequestBody LoginRequest request) {
        authService.requestLoginCode(request.email(), request.password());
        return ResponseEntity.accepted().body(new MessageResponse("Sign-in verification code sent to your email."));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifiedResponse> verify(
            @Valid @RequestBody VerificationRequest request,
            HttpServletResponse response) {
        EmailAuthService.Verification verification = authService.verifyCode(request.email(), request.code());
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, verification.sessionToken())
                .httpOnly(true)
                .secure(verification.secureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(verification.maxAgeHours()))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(new VerifiedResponse(verification.email(), verification.name()));
    }

    @GetMapping("/session")
    public ResponseEntity<MessageResponse> session(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken) {
        if (!authService.hasValidSession(sessionToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Sign-in required."));
        }
        return ResponseEntity.ok(new MessageResponse("Session is valid."));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken,
            HttpServletResponse response) {
        authService.logout(sessionToken);
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(new MessageResponse("Signed out."));
    }

    @ExceptionHandler(EmailAuthService.AuthException.class)
    public ResponseEntity<MessageResponse> handleAuthException(EmailAuthService.AuthException exception) {
        return ResponseEntity.status(exception.status()).body(new MessageResponse(exception.getMessage()));
    }

    public record RegistrationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {}

    public record VerificationRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 6, max = 6) String code) {}

    public record MessageResponse(String message) {}
    public record VerifiedResponse(String email, String name) {}
}
