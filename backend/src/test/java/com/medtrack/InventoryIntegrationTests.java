package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.auth.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5433/medtrack_test}")
@ActiveProfiles("test")
class InventoryIntegrationTests {
  @LocalServerPort int port;
  @Autowired AuthService auth;
  @Autowired JdbcTemplate jdbc;
  final ObjectMapper json = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  String access;
  UUID owner;

  @BeforeEach
  void setup() {
    access = login();
  }

  private String login() {
    String email = UUID.randomUUID() + "@example.invalid";
    owner =
        auth.register(new AuthDtos.Register(email, "fictional-password-123", "Europe/Istanbul"))
            .id();
    return auth.login(new AuthDtos.Login(email, "fictional-password-123")).accessToken();
  }

  private HttpResponse<String> call(String method, String path, Object body, String token, UUID key)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (key != null) request.header("Idempotency-Key", key.toString());
    return http.send(
        request
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode node(HttpResponse<String> response, int status) throws Exception {
    assertEquals(status, response.statusCode(), () -> "HTTP " + response.statusCode());
    return json.readTree(response.body());
  }

  private void error(HttpResponse<String> response, int status, String code) throws Exception {
    var b = node(response, status);
    assertEquals(code, b.get("code").asText());
    assertEquals(
        response.headers().firstValue("X-Request-ID").orElseThrow(), b.get("requestId").asText());
  }

  private UUID medication(String stock) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Fictional Inventory");
    body.put("unit", "tablet");
    if (stock != null) body.put("initialStock", stock);
    return UUID.fromString(
        node(call("POST", "/api/v1/medications", body, access, null), 201).get("id").asText());
  }

  private HttpResponse<String> adjust(UUID med, String reason, String quantity, UUID key)
      throws Exception {
    return call(
        "POST",
        "/api/v1/inventory/" + med + "/adjustments",
        Map.of("reason", reason, "quantity", quantity, "clientOperationId", key),
        access,
        key);
  }

  private JsonNode view(UUID med) throws Exception {
    return node(call("GET", "/api/v1/inventory/" + med, null, access, null), 200);
  }

  private long entries(UUID med) {
    return jdbc.queryForObject(
        "select count(*) from inventory_ledger where medication_id=?", Long.class, med);
  }

  @Test
  void knownOpeningCreatesExactlyOneLedgerEntryAndUnknownDoesNot() throws Exception {
    UUID known = medication("30"), unknown = medication(null), zero = medication("0");
    assertEquals("30", view(known).at("/balance/quantity").asText());
    assertEquals(1, entries(known));
    assertFalse(view(unknown).at("/balance/known").asBoolean());
    assertTrue(view(unknown).at("/balance/quantity").isNull());
    assertEquals(0, entries(unknown));
    assertTrue(view(zero).at("/balance/known").asBoolean());
    assertEquals("0", view(zero).at("/balance/quantity").asText());
    assertEquals(1, entries(zero));
  }

  @Test
  void restockAndCorrectionAppendInsteadOfOverwriting() throws Exception {
    UUID med = medication("30");
    node(adjust(med, "RESTOCK", "20", UUID.randomUUID()), 201);
    assertEquals("50", view(med).at("/balance/quantity").asText());
    var corrected = node(adjust(med, "CORRECTION", "12.25", UUID.randomUUID()), 201);
    assertEquals("-37.75", corrected.get("changeQuantity").asText());
    assertEquals("12.25", corrected.at("/balance/quantity").asText());
    assertEquals(3, entries(med));
    BigDecimal sum =
        jdbc.queryForObject(
            "select sum(change_quantity) from inventory_ledger where medication_id=?",
            BigDecimal.class,
            med);
    assertEquals(0, new BigDecimal("12.25").compareTo(sum));
  }

  @Test
  void unknownRemainsUnknownUntilAbsoluteCorrection() throws Exception {
    UUID med = medication(null);
    node(adjust(med, "RESTOCK", "20", UUID.randomUUID()), 201);
    assertFalse(view(med).at("/balance/known").asBoolean());
    var corrected = node(adjust(med, "CORRECTION", "17", UUID.randomUUID()), 201);
    assertEquals("-3", corrected.get("changeQuantity").asText());
    assertEquals("17", view(med).at("/balance/quantity").asText());
  }

  @Test
  void exactReplayReturnsOriginalResponseEvenAfterLaterChanges() throws Exception {
    UUID med = medication("30"), key = UUID.randomUUID();
    var first = node(adjust(med, "RESTOCK", "20", key), 201);
    node(adjust(med, "RESTOCK", "5", UUID.randomUUID()), 201);
    assertEquals(first, node(adjust(med, "RESTOCK", "20.0", key), 201));
    assertEquals("55", view(med).at("/balance/quantity").asText());
    assertEquals(3, entries(med));
  }

  @Test
  void reusedKeyWithDifferentContentOrMedicationIsConflict() throws Exception {
    UUID med = medication("30"), other = medication("30"), key = UUID.randomUUID();
    node(adjust(med, "RESTOCK", "20", key), 201);
    error(adjust(med, "RESTOCK", "21", key), 409, "IDEMPOTENCY_KEY_REUSED");
    error(adjust(other, "RESTOCK", "20", key), 409, "IDEMPOTENCY_KEY_REUSED");
    assertEquals(2, entries(med));
    assertEquals(1, entries(other));
  }

  @Test
  void ownerIsolationIncludesHistoryAdjustmentsAndReplay() throws Exception {
    UUID med = medication("30"), key = UUID.randomUUID();
    node(adjust(med, "RESTOCK", "20", key), 201);
    access = login();
    error(call("GET", "/api/v1/inventory/" + med, null, access, null), 404, "NOT_FOUND");
    error(adjust(med, "RESTOCK", "20", key), 404, "NOT_FOUND");
    UUID own = medication("10");
    error(adjust(own, "RESTOCK", "20", key), 409, "IDEMPOTENCY_KEY_REUSED");
    assertEquals(1, entries(own));
  }

  @Test
  void idempotencyHeaderAndBodyMustMatch() throws Exception {
    UUID med = medication("30");
    var body = Map.of("reason", "RESTOCK", "quantity", "1", "clientOperationId", UUID.randomUUID());
    error(
        call("POST", "/api/v1/inventory/" + med + "/adjustments", body, access, null),
        400,
        "INVALID_REQUEST");
    error(
        call("POST", "/api/v1/inventory/" + med + "/adjustments", body, access, UUID.randomUUID()),
        422,
        "VALIDATION_ERROR");
    assertEquals(1, entries(med));
  }

  @Test
  void invalidQuantitiesReasonsAndPaginationDoNotWrite() throws Exception {
    UUID med = medication("30");
    for (String quantity : List.of("0", "-1", "0.0000001", "10000000000000"))
      error(adjust(med, "RESTOCK", quantity, UUID.randomUUID()), 422, "VALIDATION_ERROR");
    error(adjust(med, "CORRECTION", "-1", UUID.randomUUID()), 422, "VALIDATION_ERROR");
    error(adjust(med, "UNKNOWN", "1", UUID.randomUUID()), 400, "INVALID_REQUEST");
    error(
        call("GET", "/api/v1/inventory/" + med + "?size=101", null, access, null),
        422,
        "VALIDATION_ERROR");
    assertEquals(1, entries(med));
  }

  @Test
  void authIsRequired() throws Exception {
    UUID med = medication("30"), key = UUID.randomUUID();
    error(call("GET", "/api/v1/inventory/" + med, null, null, null), 401, "UNAUTHORIZED");
    error(
        call(
            "POST",
            "/api/v1/inventory/" + med + "/adjustments",
            Map.of("reason", "RESTOCK", "quantity", "1", "clientOperationId", key),
            null,
            key),
        401,
        "UNAUTHORIZED");
  }

  @Test
  void parallelReplaysAndDistinctRestocksPreserveLedgerTotal() throws Exception {
    UUID med = medication("30"), key = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<HttpResponse<String>> task =
          () -> {
            gate.await();
            return adjust(med, "RESTOCK", "20", key);
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertEquals(node(a.get(15, TimeUnit.SECONDS), 201), node(b.get(15, TimeUnit.SECONDS), 201));
      assertEquals(2, entries(med));
      var c = pool.submit(() -> adjust(med, "RESTOCK", "1.5", UUID.randomUUID()));
      var d = pool.submit(() -> adjust(med, "RESTOCK", "2.25", UUID.randomUUID()));
      node(c.get(15, TimeUnit.SECONDS), 201);
      node(d.get(15, TimeUnit.SECONDS), 201);
    }
    assertEquals("53.75", view(med).at("/balance/quantity").asText());
    assertEquals(4, entries(med));
  }

  @Test
  void directLedgerEditsAreRejectedButAccountCascadeRemainsPossible() throws Exception {
    UUID med = medication("30");
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "update inventory_ledger set change_quantity=999 where medication_id=?", med));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("delete from inventory_ledger where medication_id=?", med));
    jdbc.update("delete from users where id=?", owner);
    assertEquals(0, entries(med));
  }

  @Test
  void projectionClearlyExplainsUnknownAndNoPlan() throws Exception {
    assertEquals("UNKNOWN_STOCK", view(medication(null)).at("/projection/reason").asText());
    assertEquals("NO_FUTURE_SCHEDULE", view(medication("30")).at("/projection/reason").asText());
  }

  @Test
  void movementHistoryIsBoundedAndDecimalPrecisionIsExact() throws Exception {
    UUID med = medication("0.1");
    node(adjust(med, "RESTOCK", "0.2", UUID.randomUUID()), 201);
    assertEquals("0.3", view(med).at("/balance/quantity").asText());
    var paged =
        node(call("GET", "/api/v1/inventory/" + med + "?page=1&size=1", null, access, null), 200);
    assertEquals(2, paged.at("/movements/totalElements").asInt());
    assertEquals(1, paged.at("/movements/content").size());
  }

  @Test
  void completionFailureRollsBackLedgerSnapshotAndOperation() throws Exception {
    UUID med = medication("30"), key = UUID.randomUUID();
    jdbc.execute(
        "create function test_fail_registry() returns trigger language plpgsql as $$ begin raise exception 'Synthetic transaction failure' using errcode='23514'; end; $$");
    jdbc.execute(
        "create trigger test_fail_registry before update on operation_registry for each row when (new.id='"
            + key
            + "'::uuid) execute function test_fail_registry()");
    try {
      error(adjust(med, "RESTOCK", "20", key), 409, "RESOURCE_CONFLICT");
      assertEquals(1, entries(med));
      assertEquals("30", view(med).at("/balance/quantity").asText());
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from operation_registry where id=?", Integer.class, key));
    } finally {
      jdbc.execute("drop trigger test_fail_registry on operation_registry");
      jdbc.execute("drop function test_fail_registry()");
    }
  }
}
