package com.college.feesmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize on controllers
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        // cost factor 12 — stronger than the default 10, still fast enough for login
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Real auth is done in AuthController — this suppresses Spring's auto-config
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                // Prevent clickjacking
                .frameOptions(frame -> frame.deny())
                // Content-Security-Policy — restricts where scripts/styles can load from
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline'; " +   // inline scripts needed for current HTML
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src https://fonts.gstatic.com; " +
                    "img-src 'self' data:; " +
                    "connect-src 'self'"
                ))
            )
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers(
                    "/auth/login",
                    "/auth/login/student",
                    "/auth/register/student",
                    "/auth/register/hod",
                    "/auth/register/admin",
                    "/auth/register/principal",
                    "/auth/otp/send",
                    "/auth/otp/verify",
                    "/auth/otp/reset-password",
                    "/departments/all",     // needed for registration form dropdown
                    "/departments/*/payment-status",
                    "/principal/**",
                    "/exam-controller/register"
                ).permitAll()
                // Hall ticket — ADMIN only (Exam Controller role)
                // Students cannot access /hall-ticket/** endpoints directly
                .requestMatchers("/hall-ticket/**").hasRole("ADMIN")
                // Photo endpoints — both view and upload are public.
                // Upload is called by admin panel during registration (no session token),
                // and the entity ID in the path already scopes access.
                .requestMatchers("/photos/view/**").permitAll()
                .requestMatchers("/photos/upload/**").permitAll()
                // Everything else
                .anyRequest().permitAll()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow all origins (supports file://, any localhost port, and live server)
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Content-Type", "Authorization",
            "X-Admin-Username", "X-Admin-Password"
        ));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}