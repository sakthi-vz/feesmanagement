package com.college.feesmanagement.controller;

import com.college.feesmanagement.service.OtpService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth/otp")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendOtp(
            @RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            if (email == null || email.isBlank())
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email address is required"));

            String masked = otpService.sendOtp(email);
            return ResponseEntity.ok(Map.of(
                    "message",     "OTP sent to your registered email",
                    "maskedEmail", masked
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp   = body.get("otp");
        if (email == null || otp == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "email and otp are required"));

        boolean valid = otpService.verifyOtp(email, otp);
        if (valid)
            return ResponseEntity.ok(Map.of("valid", true, "message", "OTP verified"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("valid", false, "error", "Invalid or expired OTP"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestBody Map<String, String> body) {
        try {
            String email       = body.get("email");
            String otp         = body.get("otp");
            String newPassword = body.get("newPassword");

            if (email == null || otp == null || newPassword == null)
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "email, otp and newPassword are required"));

            if (newPassword.length() < 6)
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password must be at least 6 characters"));

            otpService.resetPassword(email, otp, newPassword);
            return ResponseEntity.ok(
                    Map.of("message", "Password reset successfully. Please sign in."));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}