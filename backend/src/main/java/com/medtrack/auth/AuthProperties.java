package com.medtrack.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medtrack.auth")
public record AuthProperties(
    String jwtSecret,
    String issuer,
    String audience,
    Duration accessTtl,
    Duration refreshTtl,
    int rateLimit) {
  public AuthProperties {
    if (jwtSecret == null || jwtSecret.length() < 32)
      throw new IllegalArgumentException("JWT_SECRET must have at least 32 characters");
    if (issuer == null
        || audience == null
        || accessTtl == null
        || accessTtl.isNegative()
        || accessTtl.isZero()
        || refreshTtl == null
        || refreshTtl.compareTo(accessTtl) <= 0
        || rateLimit < 1)
      throw new IllegalArgumentException("Invalid authentication configuration");
  }
}
