package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.auth.*;
import com.medtrack.medications.Medication;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5433/medtrack_test}")
@ActiveProfiles("test")
@Import(MedicationIntegrationTests.NanosecondClock.class)
@ExtendWith(OutputCaptureExtension.class)
class MedicationIntegrationTests {
  @LocalServerPort int port;
  @Autowired AuthService auth;
  @Autowired JdbcTemplate jdbc;
  final ObjectMapper json = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  String access;
  UUID owner;

  private String login() {
    String email = UUID.randomUUID() + "@example.invalid";
    var account =
        auth.register(new AuthDtos.Register(email, "fictional-password-123", "Europe/Istanbul"));
    owner = account.id();
    return auth.login(new AuthDtos.Login(email, "fictional-password-123")).accessToken();
  }

  @BeforeEach
  void setup() {
    access = login();
  }

  private HttpResponse<String> call(String method, String path, Object input, String token)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
    if (token != null) request.header("Authorization", "Bearer " + token);
    return http.send(
        request
            .method(
                method,
                input == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode node(HttpResponse<String> response, int expected) throws Exception {
    assertEquals(
        expected, response.statusCode(), () -> "Unexpected status: " + response.statusCode());
    return json.readTree(response.body());
  }

  private void error(HttpResponse<String> response, int status, String code) throws Exception {
    var body = node(response, status);
    assertEquals(code, body.get("code").asText());
    assertEquals(
        response.headers().firstValue("X-Request-ID").orElseThrow(),
        body.get("requestId").asText());
    assertTrue(body.get("fieldErrors").isObject());
  }

  private JsonNode create(String name) throws Exception {
    return node(
        call(
            "POST",
            "/api/v1/medications",
            Map.of("name", name, "unit", "tablet", "initialStock", "30"),
            access),
        201);
  }

  private String path(JsonNode item) {
    return "/api/v1/medications/" + item.get("id").asText();
  }

  @Test
  void createReturnsLocationAndExactDecimalOpeningAmount() throws Exception {
    var response =
        call(
            "POST",
            "/api/v1/medications",
            Map.of(
                "name",
                "  Fictional Medicine A  ",
                "unit",
                " tablet ",
                "initialStock",
                "30.123456"),
            access);
    var item = node(response, 201);
    assertEquals("Fictional Medicine A", item.get("name").asText());
    assertEquals("tablet", item.get("unit").asText());
    assertEquals("30.123456", item.get("initialStock").asText());
    assertEquals(path(item), response.headers().firstValue("Location").orElseThrow());
    assertEquals(item, node(call("GET", path(item), null, access), 200));
    assertFalse(item.has("userId"));
    assertTrue(item.get("archivedAt").isNull());
    var amount =
        jdbc.queryForObject(
            "select initial_stock from medications where id=?",
            BigDecimal.class,
            UUID.fromString(item.get("id").asText()));
    assertEquals(0, new BigDecimal("30.123456").compareTo(amount));
    assertEquals(
        owner,
        jdbc.queryForObject(
            "select user_id from medications where id=?",
            UUID.class,
            UUID.fromString(item.get("id").asText())));
  }

  @Test
  void unknownOpeningStockIsDistinctFromZero() throws Exception {
    var unknown =
        node(
            call(
                "POST",
                "/api/v1/medications",
                Map.of("name", "Fictional Unknown", "unit", "ml"),
                access),
            201);
    var zero =
        node(
            call(
                "POST",
                "/api/v1/medications",
                Map.of("name", "Fictional Zero", "unit", "ml", "initialStock", "0"),
                access),
            201);
    assertTrue(unknown.get("initialStock").isNull());
    assertEquals("0", zero.get("initialStock").asText());
  }

  @Test
  void validationRejectsBlankLongAndInvalidAmounts() throws Exception {
    for (var input :
        List.of(
            Map.of("name", " ", "unit", "tablet"),
            Map.of("name", "Fictional", "unit", " "),
            Map.of("name", "x".repeat(201), "unit", "tablet"),
            Map.of("name", "Fictional", "unit", "x".repeat(41)),
            Map.of("name", "Fictional", "unit", "tablet", "initialStock", "-1"),
            Map.of("name", "Fictional", "unit", "tablet", "initialStock", "0.0000001"),
            Map.of("name", "Fictional", "unit", "tablet", "initialStock", "10000000000000")))
      error(call("POST", "/api/v1/medications", input, access), 422, "VALIDATION_ERROR");
    error(
        call("POST", "/api/v1/medications", Map.of("unit", "tablet"), access),
        422,
        "VALIDATION_ERROR");
  }

  @Test
  void authenticationRequiredForEveryMedicationOperation() throws Exception {
    var item = create("Fictional Auth");
    error(
        call("POST", "/api/v1/medications", Map.of("name", "Fictional", "unit", "tablet"), null),
        401,
        "UNAUTHORIZED");
    error(call("GET", "/api/v1/medications", null, null), 401, "UNAUTHORIZED");
    error(call("GET", path(item), null, null), 401, "UNAUTHORIZED");
    error(call("PATCH", path(item), Map.of("archived", true), null), 401, "UNAUTHORIZED");
  }

  @Test
  void otherUserCannotReadEditArchiveOrListMedication() throws Exception {
    var own = create("Fictional Private");
    String original = access;
    access = login();
    error(call("GET", path(own), null, access), 404, "NOT_FOUND");
    error(call("PATCH", path(own), Map.of("name", "Fictional Tampered"), access), 404, "NOT_FOUND");
    error(call("PATCH", path(own), Map.of("archived", true), access), 404, "NOT_FOUND");
    var page = node(call("GET", "/api/v1/medications?includeArchived=true", null, access), 200);
    assertEquals(0, page.get("totalElements").asInt());
    assertEquals(own, node(call("GET", path(own), null, original), 200));
  }

  @Test
  void partialPatchPreservesOpeningStockAndOtherFields() throws Exception {
    var item = create("Fictional Original");
    var changed = node(call("PATCH", path(item), Map.of("name", "Fictional Updated"), access), 200);
    assertEquals("Fictional Updated", changed.get("name").asText());
    assertEquals("tablet", changed.get("unit").asText());
    assertEquals("30", changed.get("initialStock").asText());
    assertEquals(item.get("createdAt"), changed.get("createdAt"));
    error(
        call("PATCH", path(item), Map.of("unit", "capsule"), access),
        409,
        "MEDICATION_UNIT_LOCKED");
  }

  @Test
  void archiveIsIdempotentAndRetainsOwnedRecord() throws Exception {
    var item = create("Fictional Archive");
    var first = node(call("PATCH", path(item), Map.of("archived", true), access), 200);
    assertFalse(first.get("archivedAt").isNull());
    var again = node(call("PATCH", path(item), Map.of("archived", true), access), 200);
    assertEquals(first, again);
    assertEquals(
        0,
        node(call("GET", "/api/v1/medications", null, access), 200).get("totalElements").asInt());
    assertEquals(
        1,
        node(call("GET", "/api/v1/medications?includeArchived=true", null, access), 200)
            .get("totalElements")
            .asInt());
    assertEquals(first, node(call("GET", path(item), null, access), 200));
    error(call("PATCH", path(item), Map.of("archived", false), access), 422, "VALIDATION_ERROR");
    error(call("DELETE", path(item), null, access), 405, "METHOD_NOT_ALLOWED");
  }

  @Test
  void paginationIsBoundedStableAndOwnerScoped() throws Exception {
    var a = create("Fictional First");
    var b = create("Fictional Second");
    var c = create("Fictional Third");
    var first = node(call("GET", "/api/v1/medications?size=2", null, access), 200);
    assertEquals(3, first.get("totalElements").asInt());
    assertEquals(2, first.get("totalPages").asInt());
    assertEquals(c.get("id"), first.get("content").get(0).get("id"));
    assertEquals(b.get("id"), first.get("content").get(1).get("id"));
    var second = node(call("GET", "/api/v1/medications?size=2&page=1", null, access), 200);
    assertEquals(a.get("id"), second.get("content").get(0).get("id"));
    for (String query : List.of("size=0", "size=101", "page=-1", "page=10001"))
      error(call("GET", "/api/v1/medications?" + query, null, access), 422, "VALIDATION_ERROR");
  }

  @Test
  void unsupportedFieldsCannotSpoofOwnerOrOverwriteOpeningStock() throws Exception {
    var item = create("Fictional Immutable");
    for (var body :
        List.of(
            Map.of("userId", UUID.randomUUID().toString(), "name", "Fictional", "unit", "tablet"),
            Map.of("name", "Fictional", "unit", "tablet", "id", UUID.randomUUID().toString())))
      error(call("POST", "/api/v1/medications", body, access), 400, "INVALID_REQUEST");
    error(call("PATCH", path(item), Map.of("initialStock", "999"), access), 400, "INVALID_REQUEST");
    error(call("PATCH", path(item), Map.of(), access), 422, "VALIDATION_ERROR");
    error(call("PATCH", path(item), Map.of("name", "  "), access), 422, "VALIDATION_ERROR");
    assertEquals(item, node(call("GET", path(item), null, access), 200));
  }

  @Test
  void unknownIdIs404AndMalformedIdIs400() throws Exception {
    error(call("GET", "/api/v1/medications/" + UUID.randomUUID(), null, access), 404, "NOT_FOUND");
    error(
        call(
            "PATCH",
            "/api/v1/medications/" + UUID.randomUUID(),
            Map.of("name", "Fictional"),
            access),
        404,
        "NOT_FOUND");
    error(call("GET", "/api/v1/medications/not-a-uuid", null, access), 400, "INVALID_REQUEST");
  }

  @Test
  void concurrentPartialUpdatesPreserveBothChanges() throws Exception {
    var item =
        node(
            call(
                "POST",
                "/api/v1/medications",
                Map.of("name", "Fictional Concurrent", "unit", "tablet"),
                access),
            201);
    String token = access;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                gate.await();
                return call("PATCH", path(item), Map.of("name", "Fictional Renamed"), token);
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return call("PATCH", path(item), Map.of("unit", "capsule"), token);
              });
      gate.countDown();
      node(a.get(10, TimeUnit.SECONDS), 200);
      node(b.get(10, TimeUnit.SECONDS), 200);
    }
    var result = node(call("GET", path(item), null, access), 200);
    assertEquals("Fictional Renamed", result.get("name").asText());
    assertEquals("capsule", result.get("unit").asText());
  }

  @Test
  void openApiDocumentsBearerCrudAndSwaggerAssetsAreAvailable() throws Exception {
    var spec = node(call("GET", "/v3/api-docs", null, null), 200);
    assertEquals("bearer", spec.at("/components/securitySchemes/bearerAuth/scheme").asText());
    assertTrue(spec.get("paths").has("/api/v1/medications"));
    assertTrue(spec.at("/paths/~1api~1v1~1medications~1{id}").has("patch"));
    assertTrue(spec.at("/paths/~1api~1v1~1medications/post/responses").has("201"));
    assertTrue(spec.at("/paths/~1api~1v1~1auth~1login/post/security").isArray());
    assertEquals(0, spec.at("/paths/~1api~1v1~1auth~1login/post/security").size());
    assertFalse(spec.get("paths").has("/__test/owners/{owner}"));
    assertEquals(200, call("GET", "/swagger-ui/index.html", null, null).statusCode());
    var config = node(call("GET", "/v3/api-docs/swagger-config", null, null), 200);
    assertFalse(config.get("persistAuthorization").asBoolean());
  }

  @Test
  void medicationNamesAndAmountsAreNotLogged(CapturedOutput output) throws Exception {
    String unique = "Fictional Sensitive " + UUID.randomUUID();
    var item = create(unique);
    call("GET", path(item), null, access);
    call("GET", "/api/v1/medications", null, access);
    call("PATCH", path(item), Map.of("name", unique, "unit", "x".repeat(41)), access);
    assertFalse(output.getAll().contains(unique));
  }

  @Test
  void timestampsUsePostgresMicrosecondsBeforeFirstResponse() {
    Instant nanos = Instant.parse("2026-10-22T15:00:00.123456789Z");
    var medication =
        new Medication(
            UUID.randomUUID(), UUID.randomUUID(), "Fictional Precision", "tablet", null, nanos);
    assertEquals(Instant.parse("2026-10-22T15:00:00.123456Z"), medication.createdAt());
    medication.archive(nanos);
    assertEquals(Instant.parse("2026-10-22T15:00:00.123456Z"), medication.archivedAt());
  }

  @TestConfiguration
  static class NanosecondClock {
    @Bean
    @Primary
    Clock highPrecisionClock() {
      // Reproduce Linux sub-microsecond timestamps even on a macOS host clock.
      return Clock.offset(Clock.systemUTC(), Duration.ofNanos(789));
    }
  }
}
