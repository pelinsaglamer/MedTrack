package com.medtrack.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final AuthProperties properties;
  private final Clock clock;
  private final SessionStore sessions;
  private final SecureRandom random = new SecureRandom();

  public TokenService(
      JwtEncoder encoder, AuthProperties properties, Clock clock, SessionStore sessions) {
    this.encoder = encoder;
    this.properties = properties;
    this.clock = clock;
    this.sessions = sessions;
  }

  public AuthDtos.Tokens issue(UUID owner, UUID sessionId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = clock.instant();
    sessions.addToken(hash(refresh), sessionId, now);
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(java.util.List.of(properties.audience()))
            .subject(owner.toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTtl()))
            .id(UUID.randomUUID().toString())
            .claim("sid", sessionId.toString())
            .build();
    String access =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new AuthDtos.Tokens(access, "Bearer", properties.accessTtl().toSeconds(), refresh);
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }
}
