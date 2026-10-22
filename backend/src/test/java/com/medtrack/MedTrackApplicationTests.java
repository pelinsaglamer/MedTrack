package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.auth.AuthDtos;
import com.medtrack.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5433/medtrack_test}"
    })
@ActiveProfiles("test")
@Import(MedTrackApplicationTests.Fixtures.class)
class MedTrackApplicationTests {
  @LocalServerPort int port;
  @Autowired AuthService auth;
  String bearer;

  @BeforeEach
  void authenticate() {
    String email = UUID.randomUUID() + "@example.invalid";
    auth.register(new AuthDtos.Register(email, "fictional-password-123", "Europe/Istanbul"));
    bearer = auth.login(new AuthDtos.Login(email, "fictional-password-123")).accessToken();
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  private HttpResponse<String> call(String path, String method, String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + bearer)
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void healthIncludesDatabaseWithoutLeakingDetails() throws Exception {
    var response = call("/actuator/health", "GET", null);
    assertEquals(200, response.statusCode());
    assertEquals("UP", json.readTree(response.body()).get("status").asText());
    assertFalse(response.body().contains("components"));
    UUID.fromString(response.headers().firstValue("X-Request-ID").orElseThrow());
  }

  @Test
  void migrationIsValidAndRepeatableWithoutChanges() {
    flyway.validate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'users' and table_schema = 'public'",
            Integer.class));
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success = true", Integer.class));
  }

  @Test
  void emailUniquenessIsCaseInsensitive() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into users(id,email,password_hash,timezone) values (?, ?, ?, ?)",
        id,
        "fictional@example.invalid",
        "fixture-hash-not-a-password",
        "Europe/Istanbul");
    try {
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () ->
              jdbc.update(
                  "insert into users(id,email,password_hash,timezone) values (?, ?, ?, ?)",
                  UUID.randomUUID(),
                  "FICTIONAL@example.invalid",
                  "fixture-hash-not-a-password",
                  "Europe/Istanbul"));
    } finally {
      jdbc.update("delete from users where id = ?", id);
    }
  }

  private JsonNode assertError(HttpResponse<String> response, int status, String code)
      throws Exception {
    assertEquals(status, response.statusCode());
    JsonNode node = json.readTree(response.body());
    assertEquals(code, node.get("code").asText());
    assertEquals(
        response.headers().firstValue("X-Request-ID").orElseThrow(),
        node.get("requestId").asText());
    assertTrue(node.has("message"));
    assertTrue(node.get("fieldErrors").isObject());
    return node;
  }

  @Test
  void validationUses422() throws Exception {
    var node =
        assertError(call("/__test/validate", "POST", "{\"name\":\"\"}"), 422, "VALIDATION_ERROR");
    assertTrue(node.get("fieldErrors").has("name"));
  }

  @Test
  void malformedJsonUses400() throws Exception {
    assertError(call("/__test/validate", "POST", "{"), 400, "INVALID_REQUEST");
  }

  @Test
  void unknownRouteUses404() throws Exception {
    assertError(call("/api/v1/does-not-exist", "GET", null), 404, "NOT_FOUND");
  }

  @Test
  void wrongMethodUses405() throws Exception {
    assertError(call("/__test/validate", "GET", null), 405, "METHOD_NOT_ALLOWED");
  }

  @Test
  void unexpectedErrorDoesNotLeakSensitiveDetails() throws Exception {
    var response = call("/__test/failure", "GET", null);
    assertError(response, 500, "INTERNAL_ERROR");
    assertFalse(response.body().contains("sensitive-fixture"));
  }

  @TestConfiguration
  static class Fixtures {
    @Bean
    TestEndpoints endpoints() {
      return new TestEndpoints();
    }
  }

  @RestController
  static class TestEndpoints {
    record Input(@NotBlank String name) {}

    @PostMapping("/__test/validate")
    void validate(@Valid @RequestBody Input input) {}

    @GetMapping("/__test/failure")
    void failure() {
      throw new IllegalStateException("sensitive-fixture");
    }
  }
}
