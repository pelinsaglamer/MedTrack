package com.medtrack.auth;

import com.medtrack.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final SessionStore sessions;
  private final TokenService tokens;
  private final AuthProperties properties;
  private final Clock clock;
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      PasswordEncoder passwords,
      SessionStore sessions,
      TokenService tokens,
      AuthProperties properties,
      Clock clock) {
    this.users = users;
    this.passwords = passwords;
    this.sessions = sessions;
    this.tokens = tokens;
    this.properties = properties;
    this.clock = clock;
    this.dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public AuthDtos.Account register(AuthDtos.Register input) {
    passwordBytes(input.password());
    if (!ZoneId.getAvailableZoneIds().contains(input.timezone()))
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "Request validation failed",
          Map.of("timezone", "Invalid IANA timezone"));
    User user =
        new User(
            UUID.randomUUID(),
            normalize(input.email()),
            passwords.encode(input.password()),
            input.timezone(),
            clock.instant());
    users.saveAndFlush(user);
    return AuthDtos.Account.from(user);
  }

  @Transactional
  public AuthDtos.Tokens login(AuthDtos.Login input) {
    passwordBytes(input.password());
    User user = users.findByEmail(normalize(input.email())).orElse(null);
    boolean match =
        passwords.matches(input.password(), user == null ? dummyHash : user.passwordHash());
    if (user == null || !match) throw invalid();
    UUID sessionId = UUID.randomUUID();
    sessions.create(
        sessionId, user.id(), clock.instant(), clock.instant().plus(properties.refreshTtl()));
    return tokens.issue(user.id(), sessionId);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthDtos.Tokens refresh(AuthDtos.Refresh input) {
    String hash = TokenService.hash(input.refreshToken());
    var session = sessions.lock(hash);
    if (session == null) throw invalid();
    if (sessions.consumed(hash)) {
      // Must commit revocation even though this request returns 401.
      sessions.revoke(session.id(), clock.instant());
      throw invalid();
    }
    if (session.revoked() || !session.expiresAt().isAfter(clock.instant())) throw invalid();
    sessions.consume(hash, clock.instant());
    return tokens.issue(session.userId(), session.id());
  }

  @Transactional
  public void logout(AuthDtos.Refresh input) {
    var session = sessions.lock(TokenService.hash(input.refreshToken()));
    if (session != null) sessions.revoke(session.id(), clock.instant());
  }

  private void passwordBytes(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "Request validation failed",
          Map.of("password", "Password must not exceed 72 UTF-8 bytes"));
  }

  private String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private ApiException invalid() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials or session");
  }
}
