package com.greenops.scrapper.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Email-code authentication for the local GreenOps account flow. */
@Service
public final class EmailAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailAuthService.class);
    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final int CODE_LIFETIME_MINUTES = 10;
    private static final int SESSION_LIFETIME_HOURS = 8;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final boolean secureCookie;
    private final byte[] codePepper = randomBytes(32);
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastCodeSent = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempts> loginAttempts = new ConcurrentHashMap<>();

    public EmailAuthService(
            JavaMailSender mailSender,
            @Value("${GREENOPS_AUTH_MAIL_FROM:no-reply@greenops.local}") String mailFrom,
            @Value("${greenops.auth.cookie-secure:false}") boolean secureCookie) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.secureCookie = secureCookie;
    }

    public synchronized void register(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        Account existing = accounts.get(normalizedEmail);
        if (existing != null) {
            if (existing.emailVerified()) {
                throw new AuthException(409, "An account with this email already exists. Try signing in.");
            }
            if (!passwordMatches(password, existing.passwordHash())) {
                throw new AuthException(409, "An account with this email is pending verification. Use its password to resend the code.");
            }
            issueCode(normalizedEmail, Purpose.REGISTRATION);
            return;
        }

        Account account = new Account(name.trim(), passwordHash(password), false);
        accounts.put(normalizedEmail, account);
        try {
            issueCode(normalizedEmail, Purpose.REGISTRATION);
        } catch (RuntimeException exception) {
            accounts.remove(normalizedEmail, account);
            throw exception;
        }
    }

    public synchronized void requestLoginCode(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        Account account = accounts.get(normalizedEmail);
        if (account == null || !passwordMatches(password, account.passwordHash())) {
            LoginAttempts attempts = loginAttempts.get(normalizedEmail);
            if (attempts == null || attempts.windowStartedAt().plus(Duration.ofMinutes(15)).isBefore(Instant.now())) {
                attempts = new LoginAttempts(Instant.now(), 0);
            }
            attempts = attempts.increment();
            loginAttempts.put(normalizedEmail, attempts);
            if (attempts.count() >= 8) {
                throw new AuthException(429, "Too many sign-in attempts. Try again in 15 minutes.");
            }
            throw new AuthException(401, "Email or password is incorrect.");
        }
        loginAttempts.remove(normalizedEmail);
        issueCode(normalizedEmail, Purpose.LOGIN);
    }

    public synchronized Verification verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        Challenge challenge = challenges.get(normalizedEmail);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            challenges.remove(normalizedEmail);
            throw new AuthException(400, "This code has expired. Request a new one.");
        }

        if (!MessageDigest.isEqual(challenge.codeHash(), hashCode(code))) {
            if (challenge.failedAttempts() + 1 >= MAX_CODE_ATTEMPTS) {
                challenges.remove(normalizedEmail);
                throw new AuthException(400, "Too many incorrect codes. Request a new one.");
            }
            challenges.put(normalizedEmail, challenge.withFailedAttempt());
            throw new AuthException(400, "That verification code is not valid.");
        }

        challenges.remove(normalizedEmail);
        Account account = accounts.get(normalizedEmail);
        if (account == null) throw new AuthException(400, "Account not found. Please register again.");
        accounts.put(normalizedEmail, account.verified());

        String sessionToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        sessions.put(sessionToken, new Session(normalizedEmail, Instant.now().plus(Duration.ofHours(SESSION_LIFETIME_HOURS))));
        return new Verification(normalizedEmail, account.name(), sessionToken, SESSION_LIFETIME_HOURS, secureCookie);
    }

    public boolean hasValidSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) return false;
        Session session = sessions.get(sessionToken);
        if (session == null) return false;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionToken, session);
            return false;
        }
        return true;
    }

    public void logout(String sessionToken) {
        if (sessionToken != null) sessions.remove(sessionToken);
    }

    private void issueCode(String email, Purpose purpose) {
        Instant lastSent = lastCodeSent.get(email);
        if (lastSent != null && lastSent.plusSeconds(60).isAfter(Instant.now())) {
            throw new AuthException(429, "Please wait a minute before requesting another code.");
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        challenges.put(email, new Challenge(hashCode(code), Instant.now().plus(Duration.ofMinutes(CODE_LIFETIME_MINUTES)), 0, purpose));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject(purpose == Purpose.REGISTRATION
                ? "Verify your GreenOps email"
                : "Your GreenOps sign-in verification code");
        message.setText("Your GreenOps verification code is " + code
                + ". It expires in " + CODE_LIFETIME_MINUTES + " minutes.\n\n"
                + "If you did not request this code, you can ignore this email.");
        try {
            mailSender.send(message);
            lastCodeSent.put(email, Instant.now());
        } catch (MailAuthenticationException exception) {
            challenges.remove(email);
            LOGGER.warn("SMTP authentication was rejected. Check SMTP_USERNAME and SMTP_PASSWORD.");
            throw new AuthException(503,
                    "SMTP rejected the credentials. Check SMTP_USERNAME and SMTP_PASSWORD; Gmail requires an App Password without spaces.");
        } catch (RuntimeException exception) {
            challenges.remove(email);
            LOGGER.warn("Verification email could not be sent; check the configured SMTP server ({}).",
                    exception.getClass().getSimpleName());
            throw new AuthException(503,
                    "Email delivery is not configured or is temporarily unavailable. Check the backend SMTP settings.");
        }
    }

    private byte[] hashCode(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codePepper, "HmacSHA256"));
            return mac.doFinal(code.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not secure verification code", exception);
        }
    }

    private String passwordHash(String password) {
        byte[] salt = randomBytes(16);
        byte[] derived = derivePassword(password, salt);
        return HexFormat.of().formatHex(salt) + ":" + HexFormat.of().formatHex(derived);
    }

    private boolean passwordMatches(String password, String savedHash) {
        try {
            String[] parts = savedHash.split(":", 2);
            byte[] salt = HexFormat.of().parseHex(parts[0]);
            byte[] expected = HexFormat.of().parseHex(parts[1]);
            return MessageDigest.isEqual(expected, derivePassword(password, salt));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private byte[] derivePassword(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not secure account password", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new AuthException(400, "Email is required.");
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private enum Purpose { REGISTRATION, LOGIN }
    private record Account(String name, String passwordHash, boolean emailVerified) {
        Account verified() { return new Account(name, passwordHash, true); }
    }
    private record Challenge(byte[] codeHash, Instant expiresAt, int failedAttempts, Purpose purpose) {
        Challenge withFailedAttempt() { return new Challenge(codeHash, expiresAt, failedAttempts + 1, purpose); }
    }
    private record Session(String email, Instant expiresAt) {}
    private record LoginAttempts(Instant windowStartedAt, int count) {
        LoginAttempts increment() { return new LoginAttempts(windowStartedAt, count + 1); }
    }

    public record Verification(String email, String name, String sessionToken, int maxAgeHours, boolean secureCookie) {}

    public static final class AuthException extends RuntimeException {
        private final int status;
        public AuthException(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
