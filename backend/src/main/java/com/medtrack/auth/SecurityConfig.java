package com.medtrack.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKey secretKey(AuthProperties properties) {
    return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(
      SecretKey key, AuthProperties properties, SessionStore sessions, Clock clock) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    var timestamp = new JwtTimestampValidator(Duration.ZERO);
    timestamp.setClock(clock);
    OAuth2TokenValidator<Jwt> identity =
        jwt -> {
          try {
            if (jwt.getExpiresAt() == null
                || jwt.getIssuedAt() == null
                || !jwt.getAudience().contains(properties.audience())
                || !sessions.active(
                    UUID.fromString(jwt.getClaimAsString("sid")),
                    UUID.fromString(jwt.getSubject()),
                    clock.instant())) return failure();
            return OAuth2TokenValidatorResult.success();
          } catch (IllegalArgumentException | NullPointerException ex) {
            return failure();
          }
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamp, new JwtIssuerValidator(properties.issuer()), identity));
    return decoder;
  }

  private OAuth2TokenValidatorResult failure() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Invalid access token", null));
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, SecurityErrors errors, AuthProperties properties, Clock clock)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/actuator/health",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, s, x) -> errors.write(q, s, 401, "UNAUTHORIZED"))
                    .accessDeniedHandler((q, s, x) -> errors.write(q, s, 403, "FORBIDDEN")))
        .oauth2ResourceServer(
            o ->
                o.jwt(Customizer.withDefaults())
                    .authenticationEntryPoint((q, s, x) -> errors.write(q, s, 401, "UNAUTHORIZED"))
                    .accessDeniedHandler((q, s, x) -> errors.write(q, s, 403, "FORBIDDEN")))
        .addFilterBefore(
            new AuthRateLimitFilter(properties, clock, errors),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
