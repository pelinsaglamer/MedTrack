package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.account.CurrentUser;
import com.medtrack.auth.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5433/medtrack_test}")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(AuthIntegrationTests.Fixtures.class)
class AuthIntegrationTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtEncoder encoder;
  @Autowired AuthProperties properties;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private static final String PASSWORD = "fictional-password-123";

  record Login(String email, JsonNode account, JsonNode tokens) {}

  private HttpResponse<String> call(String method, String path, Object body, String token)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
    if (token != null) builder.header("Authorization", "Bearer " + token);
    return http.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode node(HttpResponse<String> response, int status) throws Exception {
    assertEquals(
        status, response.statusCode(), () -> "Unexpected HTTP status: " + response.statusCode());
    return response.body().isEmpty() ? null : json.readTree(response.body());
  }

  private Login user() throws Exception {
    String email = UUID.randomUUID() + "@example.invalid";
    var account =
        node(
            call(
                "POST",
                "/api/v1/auth/register",
                Map.of("email", email, "password", PASSWORD, "timezone", "Europe/Istanbul"),
                null),
            201);
    var tokens =
        node(
            call("POST", "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null),
            200);
    return new Login(email, account, tokens);
  }

  private HttpResponse<String> refresh(String token) throws Exception {
    return call("POST", "/api/v1/auth/refresh", Map.of("refreshToken", token), null);
  }

  private HttpResponse<String> me(String token) throws Exception {
    return call("GET", "/api/v1/account/me", null, token);
  }

  private void error(HttpResponse<String> response, int status, String code) throws Exception {
    var body = node(response, status);
    assertEquals(code, body.get("code").asText());
    assertEquals(
        response.headers().firstValue("X-Request-ID").orElseThrow(),
        body.get("requestId").asText());
    UUID.fromString(body.get("requestId").asText());
    assertTrue(body.get("fieldErrors").isObject());
    assertFalse(response.body().contains(PASSWORD));
  }

  @Test
  void registerHashesPasswordAndNeverSerializesSecrets() throws Exception {
    var u = user();
    assertEquals(Set.of("id", "email", "timezone"), u.account().propertyNames());
    String stored =
        jdbc.queryForObject(
            "select password_hash from users where id=?",
            String.class,
            UUID.fromString(u.account().get("id").asText()));
    assertNotEquals(PASSWORD, stored);
    assertTrue(stored.startsWith("$2"));
    var response = me(u.tokens().get("accessToken").asText());
    assertEquals(u.account(), node(response, 200));
    assertFalse(response.body().contains("password"));
    assertEquals("Bearer", u.tokens().get("tokenType").asText());
    assertEquals(600, u.tokens().get("expiresIn").asLong());
  }

  @Test
  void duplicateAndLoginUseLocaleIndependentEmailNormalization() throws Exception {
    var u = user();
    error(
        call(
            "POST",
            "/api/v1/auth/register",
            Map.of(
                "email",
                u.email().toUpperCase(Locale.ROOT),
                "password",
                PASSWORD,
                "timezone",
                "Europe/Istanbul"),
            null),
        409,
        "RESOURCE_CONFLICT");
    node(
        call(
            "POST",
            "/api/v1/auth/login",
            Map.of("email", u.email().toUpperCase(Locale.ROOT), "password", PASSWORD),
            null),
        200);
  }

  @Test
  void unknownEmailAndWrongPasswordHaveSameResponse() throws Exception {
    var u = user();
    var wrong =
        call(
            "POST",
            "/api/v1/auth/login",
            Map.of("email", u.email(), "password", "incorrect-password"),
            null);
    var unknown =
        call(
            "POST",
            "/api/v1/auth/login",
            Map.of(
                "email", UUID.randomUUID() + "@example.invalid", "password", "incorrect-password"),
            null);
    error(wrong, 401, "INVALID_CREDENTIALS");
    error(unknown, 401, "INVALID_CREDENTIALS");
    assertEquals(
        json.readTree(wrong.body()).get("message"), json.readTree(unknown.body()).get("message"));
  }

  @Test
  void validatesEmailPasswordAndIanaTimezone() throws Exception {
    for (var input :
        List.of(
            Map.of("email", "invalid", "password", PASSWORD, "timezone", "Europe/Istanbul"),
            Map.of(
                "email",
                "fixture@example.invalid",
                "password",
                "short",
                "timezone",
                "Europe/Istanbul"),
            Map.of("email", "fixture@example.invalid", "password", PASSWORD, "timezone", "+03:00"),
            Map.of(
                "email",
                "fixture@example.invalid",
                "password",
                "ş".repeat(40),
                "timezone",
                "Europe/Istanbul")))
      error(call("POST", "/api/v1/auth/register", input, null), 422, "VALIDATION_ERROR");
  }

  @Test
  void protectedResourcesRejectMissingMalformedAndTamperedJwt() throws Exception {
    error(me(null), 401, "UNAUTHORIZED");
    error(me("invalid"), 401, "UNAUTHORIZED");
    var u = user();
    String token = u.tokens().get("accessToken").asText();
    String[] parts = token.split("[.]");
    parts[1] =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "{\"sub\":\"forged\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    error(me(String.join(".", parts)), 401, "UNAUTHORIZED");
  }

  @Test
  void accountsAndOwnershipGuardIsolateUsers() throws Exception {
    var a = user();
    var b = user();
    String access = b.tokens().get("accessToken").asText();
    assertEquals(
        b.account(),
        node(
            call(
                "GET", "/api/v1/account/me?userId=" + a.account().get("id").asText(), null, access),
            200));
    String own = b.account().get("id").asText(), foreign = a.account().get("id").asText();
    node(call("GET", "/__test/owners/" + own, null, access), 200);
    error(call("GET", "/__test/owners/" + foreign, null, access), 404, "NOT_FOUND");
    error(call("GET", "/api/v1/account/" + foreign, null, access), 404, "NOT_FOUND");
  }

  @Test
  void refreshRotatesAndOnlyHashesAreStored() throws Exception {
    var u = user();
    String old = u.tokens().get("refreshToken").asText();
    var rotated = node(refresh(old), 200);
    String next = rotated.get("refreshToken").asText();
    assertNotEquals(old, next);
    node(me(rotated.get("accessToken").asText()), 200);
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from refresh_tokens where token_hash=? and consumed_at is not null",
            Integer.class,
            TokenService.hash(old)));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from refresh_tokens where token_hash=?", Integer.class, next));
    assertEquals("no-store", refresh(next).headers().firstValue("Cache-Control").orElseThrow());
  }

  @Test
  void refreshReplayRevokesWholeFamilyAndAccess() throws Exception {
    var u = user();
    String original = u.tokens().get("refreshToken").asText();
    var next = node(refresh(original), 200);
    error(refresh(original), 401, "INVALID_CREDENTIALS");
    error(refresh(next.get("refreshToken").asText()), 401, "INVALID_CREDENTIALS");
    error(me(next.get("accessToken").asText()), 401, "UNAUTHORIZED");
  }

  @Test
  void concurrentRefreshCannotCreateTwoUsableSuccessors() throws Exception {
    var u = user();
    String token = u.tokens().get("refreshToken").asText();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<HttpResponse<String>> action =
          () -> {
            gate.await();
            return refresh(token);
          };
      var a = pool.submit(action);
      var b = pool.submit(action);
      gate.countDown();
      var responses = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
      assertEquals(
          List.of(200, 401), responses.stream().map(HttpResponse::statusCode).sorted().toList());
      var success =
          json.readTree(
              responses.stream()
                  .filter(x -> x.statusCode() == 200)
                  .findFirst()
                  .orElseThrow()
                  .body());
      error(refresh(success.get("refreshToken").asText()), 401, "INVALID_CREDENTIALS");
    }
  }

  @Test
  void logoutIsIdempotentAndDoesNotRevokeOtherSessions() throws Exception {
    var u = user();
    var second =
        node(
            call(
                "POST",
                "/api/v1/auth/login",
                Map.of("email", u.email(), "password", PASSWORD),
                null),
            200);
    var body = Map.of("refreshToken", u.tokens().get("refreshToken").asText());
    node(call("POST", "/api/v1/auth/logout", body, null), 204);
    node(call("POST", "/api/v1/auth/logout", body, null), 204);
    error(refresh(u.tokens().get("refreshToken").asText()), 401, "INVALID_CREDENTIALS");
    error(me(u.tokens().get("accessToken").asText()), 401, "UNAUTHORIZED");
    node(me(second.get("accessToken").asText()), 200);
  }

  @Test
  void expiredRefreshAndUnknownTokenAreRejected() throws Exception {
    var u = user();
    UUID owner = UUID.fromString(u.account().get("id").asText());
    jdbc.update(
        "update auth_sessions set created_at=now()-interval '2 days', expires_at=now()-interval '1 day' where user_id=?",
        owner);
    error(refresh(u.tokens().get("refreshToken").asText()), 401, "INVALID_CREDENTIALS");
    error(refresh("a".repeat(43)), 401, "INVALID_CREDENTIALS");
  }

  @Test
  void decoderRejectsExpiredWrongIssuerWrongAudienceAndMissingSession() throws Exception {
    var u = user();
    String refresh = u.tokens().get("refreshToken").asText();
    String sid =
        jdbc.queryForObject(
                "select session_id from refresh_tokens where token_hash=?",
                UUID.class,
                TokenService.hash(refresh))
            .toString();
    for (int variant = 0; variant < 4; variant++) {
      var claims =
          JwtClaimsSet.builder()
              .subject(u.account().get("id").asText())
              .issuedAt(Instant.now().minusSeconds(120))
              .expiresAt(
                  variant == 0 ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(60))
              .issuer(variant == 1 ? "wrong" : properties.issuer())
              .audience(List.of(variant == 2 ? "wrong" : properties.audience()))
              .claim("sid", variant == 3 ? UUID.randomUUID().toString() : sid)
              .build();
      String access =
          encoder
              .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
              .getTokenValue();
      error(me(access), 401, "UNAUTHORIZED");
    }
  }

  @Test
  void deletingUserInvalidatesSessionByForeignKeyCascade() throws Exception {
    var u = user();
    jdbc.update("delete from users where id=?", UUID.fromString(u.account().get("id").asText()));
    error(me(u.tokens().get("accessToken").asText()), 401, "UNAUTHORIZED");
    error(refresh(u.tokens().get("refreshToken").asText()), 401, "INVALID_CREDENTIALS");
  }

  @Test
  void logsDoNotContainCredentialsOrAccountData(CapturedOutput output) throws Exception {
    var u = user();
    call(
        "POST",
        "/api/v1/auth/register",
        Map.of("email", u.email(), "password", PASSWORD, "timezone", "Europe/Istanbul"),
        null);
    call("POST", "/api/v1/auth/login", Map.of("email", u.email(), "password", ""), null);
    me(u.tokens().get("accessToken").asText());
    assertFalse(output.getAll().contains(PASSWORD));
    assertFalse(output.getAll().contains(u.email()));
    assertFalse(output.getAll().contains(u.tokens().get("accessToken").asText()));
    assertFalse(output.getAll().contains(u.tokens().get("refreshToken").asText()));
  }

  @TestConfiguration
  static class Fixtures {
    @Bean
    OwnershipEndpoint ownershipEndpoint(CurrentUser owner) {
      return new OwnershipEndpoint(owner);
    }
  }

  @RestController
  static class OwnershipEndpoint {
    private final CurrentUser current;

    OwnershipEndpoint(CurrentUser current) {
      this.current = current;
    }

    @GetMapping("/__test/owners/{owner}")
    Map<String, UUID> owner(@PathVariable UUID owner) {
      current.requireOwnership(owner);
      return Map.of("owner", owner);
    }
  }
}
