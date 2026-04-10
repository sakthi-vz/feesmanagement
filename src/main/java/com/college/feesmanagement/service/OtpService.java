package com.college.feesmanagement.service;

import com.college.feesmanagement.entity.User;
import com.college.feesmanagement.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP service — production-hardened edition.
 *
 * Security improvements over v1:
 *   - SecureRandom (CSPRNG) instead of java.util.Random
 *   - BCrypt hash stored; constant-time comparison on verify
 *   - Account lockout after MAX_VERIFY_ATTEMPTS wrong guesses
 *   - Send-rate limiting per email (no Redis required)
 *   - Anti-enumeration: same response shape whether email exists or not
 *   - OTP invalidated immediately after successful reset (no reuse)
 *   - Raw OTP never logged anywhere
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    // ── Policy constants ──────────────────────────────────────────
    private static final int  OTP_VALIDITY_MINUTES  = 10;
    private static final int  MAX_VERIFY_ATTEMPTS   = 5;   // lock after 5 wrong guesses
    private static final int  MAX_SENDS_PER_WINDOW  = 5;   // max OTPs sent per email per window
    private static final int  RATE_WINDOW_MINUTES   = 30;  // send-rate window duration

    // ── Cryptographically secure RNG (reused — construction is expensive) ─
    // SecureRandom draws entropy from the OS CSPRNG (/dev/urandom on Linux,
    // CryptGenRandom on Windows) — unlike java.util.Random which is predictable.
    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository        userRepository;
    private final BCryptPasswordEncoder encoder;
    private final JavaMailSender        mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ── In-memory OTP store: email → OtpRecord ───────────────────
    private final ConcurrentHashMap<String, OtpRecord> otpStore = new ConcurrentHashMap<>();

    // ── Send-rate log: email → queue of send timestamps ──────────
    private final ConcurrentHashMap<String, Deque<LocalDateTime>> sendLog = new ConcurrentHashMap<>();

    public OtpService(UserRepository userRepository,
                      BCryptPasswordEncoder encoder,
                      JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.encoder        = encoder;
        this.mailSender     = mailSender;
    }

    // ── PUBLIC API ────────────────────────────────────────────────

    /**
     * Generates a secure OTP and emails it.
     * Returns a masked email for the UI ("sa****@gmail.com").
     *
     * Anti-enumeration: always returns same response shape
     * whether the email exists in the DB or not.
     */
    public String sendOtp(String email) {
        if (email == null || email.isBlank())
            throw new RuntimeException("Email address is required.");

        String key = email.trim().toLowerCase();

        // Rate limit: max MAX_SENDS_PER_WINDOW attempts per RATE_WINDOW_MINUTES
        checkSendRateLimit(key);

        // Silently ignore unknown emails — don't leak which emails exist
        User user = userRepository.findByEmail(key).orElse(null);
        if (user == null)
            return maskEmail(key); // looks like success to caller

        // SecureRandom: range 100_000–999_999 guarantees 6 digits, no leading zeros
        int    rawOtpInt = 100_000 + secureRandom.nextInt(900_000);
        String rawOtp    = String.valueOf(rawOtpInt);

        // BCrypt hash — raw OTP is never stored anywhere, only the hash
        String hashedOtp = encoder.encode(rawOtp);

        String name = user.getUsername() != null ? user.getUsername()
                    : (user.getRollNo() != null  ? user.getRollNo()  : "User");

        // Store record (fresh send resets attempt count)
        otpStore.put(key, new OtpRecord(hashedOtp,
                LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES)));

        recordSend(key);

        try {
            sendEmail(key, name, rawOtp);
            // rawOtp goes out of scope here — never stored or logged
        } catch (Exception e) {
            log.error("OTP email failed for [{}]", maskEmail(key), e);
            otpStore.remove(key); // don't leave an unsent OTP in store
            throw new RuntimeException("Failed to send OTP email. Please try again.");
        }

        return maskEmail(key);
    }

    /**
     * Verifies a submitted OTP using constant-time BCrypt comparison.
     * Tracks failed attempts and locks after MAX_VERIFY_ATTEMPTS.
     */
    public boolean verifyOtp(String email, String rawOtp) {
        String    key    = email.trim().toLowerCase();
        OtpRecord record = otpStore.get(key);

        if (record == null) return false;

        // Locked?
        if (record.locked) {
            if (LocalDateTime.now().isAfter(record.lockUntil)) {
                // Lock expired — auto-lift
                record.locked    = false;
                record.lockUntil = null;
                record.attempts  = 0;
            } else {
                long mins = ChronoUnit.MINUTES.between(LocalDateTime.now(), record.lockUntil) + 1;
                throw new RuntimeException(
                    "Too many failed attempts. Try again in " + mins + " minute(s).");
            }
        }

        // Expired?
        if (LocalDateTime.now().isAfter(record.expiry)) {
            otpStore.remove(key);
            return false;
        }

        // Max attempts exceeded?
        if (record.attempts >= MAX_VERIFY_ATTEMPTS) {
            otpStore.remove(key);
            return false;
        }

        // Constant-time BCrypt comparison (prevents timing attacks)
        boolean matches = encoder.matches(rawOtp.trim(), record.hashedOtp);

        if (matches) {
            // Leave record in place so resetPassword can re-verify in same call chain
            return true;
        } else {
            record.attempts++;
            if (record.attempts >= MAX_VERIFY_ATTEMPTS) {
                record.locked    = true;
                record.lockUntil = LocalDateTime.now().plusMinutes(RATE_WINDOW_MINUTES);
                log.warn("OTP locked for [{}] after {} failed attempts",
                        maskEmail(key), MAX_VERIFY_ATTEMPTS);
            }
            return false;
        }
    }

    /**
     * Resets the user's password after OTP verification.
     * Invalidates the OTP immediately after — prevents replay.
     */
    public void resetPassword(String email, String rawOtp, String newPassword) {
        if (newPassword == null || newPassword.length() < 8)
            throw new RuntimeException("Password must be at least 8 characters.");

        if (!verifyOtp(email, rawOtp))
            throw new RuntimeException("Invalid or expired OTP.");

        String key  = email.trim().toLowerCase();
        User   user = userRepository.findByEmail(key)
                .orElseThrow(() -> new RuntimeException("User not found."));

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        // Invalidate OTP immediately — no replay possible
        otpStore.remove(key);
        sendLog.remove(key);

        log.info("Password reset successful for [{}]", maskEmail(key));
    }

    // ── Rate limiting ─────────────────────────────────────────────

    private void checkSendRateLimit(String key) {
        sendLog.compute(key, (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RATE_WINDOW_MINUTES);
            // Remove timestamps outside the window
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff))
                deque.pollFirst();
            if (deque.size() >= MAX_SENDS_PER_WINDOW)
                throw new RuntimeException(
                    "Too many OTP requests. Please wait " + RATE_WINDOW_MINUTES
                    + " minutes before trying again.");
            return deque;
        });
    }

    private void recordSend(String key) {
        sendLog.compute(key, (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            deque.addLast(LocalDateTime.now());
            return deque;
        });
    }

    // ── Email ─────────────────────────────────────────────────────

    private void sendEmail(String to, String name, String otp) throws Exception {
        MimeMessage           message = mailSender.createMimeMessage();
        MimeMessageHelper     helper  = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, "CEMS — Exam Management");
        helper.setTo(to);
        helper.setSubject("CEMS — Your OTP for Password Reset");
        helper.setText(buildHtml(name, otp), true);
        mailSender.send(message);
    }

    private String buildHtml(String name, String otp) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:20px;margin:0;">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="max-width:520px;margin:auto;background:#ffffff;
                            border-radius:10px;overflow:hidden;
                            box-shadow:0 2px 12px rgba(0,0,0,.12);">
                <tr>
                  <td style="background:#2f52e0;padding:28px 32px;text-align:center;">
                    <h2 style="color:#ffffff;margin:0;font-size:20px;letter-spacing:.5px;">
                      Password Reset OTP
                    </h2>
                    <p style="color:#c7d0f8;margin:6px 0 0;font-size:13px;">
                      Mohamed Sathak A.J. College of Engineering
                    </p>
                  </td>
                </tr>
                <tr>
                  <td style="padding:32px;">
                    <p style="margin:0 0 12px;color:#333;font-size:15px;">
                      Hello <strong>%s</strong>,
                    </p>
                    <p style="margin:0 0 24px;color:#555;font-size:14px;line-height:1.6;">
                      Use the code below to reset your CEMS account password.
                      This code is valid for <strong>%d minutes</strong> and can only be used once.
                    </p>
                    <div style="text-align:center;margin:28px 0;">
                      <span style="display:inline-block;background:#f0f4ff;
                                   border:2px dashed #2f52e0;border-radius:10px;
                                   padding:18px 44px;font-size:38px;font-weight:bold;
                                   letter-spacing:10px;color:#1a1a1a;
                                   font-family:'Courier New',monospace;">
                        %s
                      </span>
                    </div>
                    <p style="margin:0;color:#888;font-size:12px;line-height:1.6;">
                      If you didn't request a password reset, you can safely ignore this email.
                    </p>
                  </td>
                </tr>
                <tr>
                  <td style="background:#f9f9f9;padding:14px 32px;text-align:center;
                             border-top:1px solid #eee;">
                    <p style="margin:0;color:#aaa;font-size:11px;">
                      This is an automated message — please do not reply. &nbsp;|&nbsp;
                      CEMS · College Exam Management System
                    </p>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(escapeHtml(name), OTP_VALIDITY_MINUTES, otp);
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Masks email for safe logging: samuel@gmail.com → sa****@gmail.com
     * (matches EmailMaskingUtil algorithm from uploaded library)
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int    at      = email.indexOf('@');
        String local   = email.substring(0, at);
        String domain  = email.substring(at);
        int    visible = Math.min(2, local.length());
        return local.substring(0, visible) + "****" + domain;
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Inner record class ────────────────────────────────────────

    /**
     * Holds one OTP session.
     * hashedOtp = BCrypt(rawOtp) — plaintext is NEVER stored here.
     */
    private static class OtpRecord {
        final String        hashedOtp;
        final LocalDateTime expiry;
        int                 attempts  = 0;
        boolean             locked    = false;
        LocalDateTime       lockUntil = null;

        OtpRecord(String hashedOtp, LocalDateTime expiry) {
            this.hashedOtp = hashedOtp;
            this.expiry    = expiry;
        }
    }
}