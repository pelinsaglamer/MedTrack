package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.auth.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
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
@Import(DoseActionIntegrationTests.TimeConfig.class)
class DoseActionIntegrationTests {
  @LocalServerPort int port;
  @Autowired AuthService auth;
  @Autowired JdbcTemplate jdbc;
  final ObjectMapper json = new ObjectMapper();
  final HttpClient http = HttpClient.newHttpClient();
  String token;
  UUID owner;
  UUID med;
  UUID due;
  UUID future;

  @Test
  void doseDetailRefreshesStateAndHidesOtherOwners() throws Exception {
    var detail = node(call("GET", "/api/v1/doses/" + due, null, null, token), 200);
    assertEquals(due.toString(), detail.get("id").asText());
    assertEquals("DUE", detail.get("status").asText());
    assertEquals(
        "PENDING",
        node(call("GET", "/api/v1/doses/" + future, null, null, token), 200)
            .get("status")
            .asText());
    String other = login();
    error(call("GET", "/api/v1/doses/" + due, null, null, other), 404, "NOT_FOUND");
    error(call("GET", "/api/v1/doses/" + UUID.randomUUID(), null, null, other), 404, "NOT_FOUND");
    error(call("GET", "/api/v1/doses/" + due, null, null, null), 401, "UNAUTHORIZED");
  }

  @Test
  void scheduleHistoryIsBoundedAndOwnerScoped() throws Exception {
    var response =
        node(
            call(
                "GET",
                "/api/v1/medications/" + med + "/schedules?page=0&size=1",
                null,
                null,
                token),
            200);
    assertEquals(1, response.get("content").size());
    assertEquals(1, response.get("content").get(0).get("version").asInt());
    assertEquals(
        422,
        call("GET", "/api/v1/medications/" + med + "/schedules?size=101", null, null, token)
            .statusCode());
    String other = login();
    error(
        call("GET", "/api/v1/medications/" + med + "/schedules", null, null, other),
        404,
        "NOT_FOUND");
  }

  @BeforeEach
  void setup() throws Exception {
    token = login();
    med = createMedication("30");
    plan(med, "1", List.of("09:00", "21:00"));
    var page = day("2026-10-22");
    due = UUID.fromString(page.get("content").get(0).get("id").asText());
    future = UUID.fromString(page.get("content").get(1).get("id").asText());
  }

  private String login() {
    String email = UUID.randomUUID() + "@example.invalid";
    owner =
        auth.register(new AuthDtos.Register(email, "fictional-password-123", "Europe/Istanbul"))
            .id();
    return auth.login(new AuthDtos.Login(email, "fictional-password-123")).accessToken();
  }

  private HttpResponse<String> call(
      String method, String path, Object input, UUID key, String access) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
    if (access != null) b.header("Authorization", "Bearer " + access);
    if (key != null) b.header("Idempotency-Key", key.toString());
    return http.send(
        b.method(
                method,
                input == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode node(HttpResponse<String> response, int status) throws Exception {
    assertEquals(status, response.statusCode(), () -> "HTTP " + response.statusCode());
    return json.readTree(response.body());
  }

  private void error(HttpResponse<String> response, int status, String code) throws Exception {
    var n = node(response, status);
    assertEquals(code, n.get("code").asText());
    assertEquals(
        response.headers().firstValue("X-Request-ID").orElseThrow(), n.get("requestId").asText());
  }

  private UUID createMedication(String stock) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Fictional Action");
    body.put("unit", "tablet");
    if (stock != null) body.put("initialStock", stock);
    return UUID.fromString(
        node(call("POST", "/api/v1/medications", body, null, token), 201).get("id").asText());
  }

  private void plan(UUID medication, String quantity, List<String> times) throws Exception {
    node(
        call(
            "POST",
            "/api/v1/medications/" + medication + "/schedules",
            Map.of(
                "times",
                times,
                "quantityPerDose",
                quantity,
                "timezone",
                "Europe/Istanbul",
                "startDate",
                "2026-10-20"),
            null,
            token),
        201);
  }

  private JsonNode day(String date) throws Exception {
    return node(call("GET", "/api/v1/doses?date=" + date + "&size=100", null, null, token), 200);
  }

  private Map<String, Object> body(String action, UUID key) {
    return new HashMap<>(
        Map.of("action", action, "clientOperationId", key, "recordedAt", "2026-10-22T06:05:00Z"));
  }

  private HttpResponse<String> action(UUID id, String type, UUID key) throws Exception {
    return call("POST", "/api/v1/doses/" + id + "/actions", body(type, key), key, token);
  }

  private JsonNode stock(UUID medication) throws Exception {
    return node(call("GET", "/api/v1/inventory/" + medication, null, null, token), 200);
  }

  private long actions(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from dose_actions where occurrence_id=?", Long.class, id);
  }

  private long consumption(UUID medication) {
    return jdbc.queryForObject(
        "select count(*) from inventory_ledger where medication_id=? and reason='CONSUMPTION'",
        Long.class,
        medication);
  }

  @Test
  void takenIsOneActionAndOneDecrementWithActualTimestamp() throws Exception {
    var result = node(action(due, "TAKEN", UUID.randomUUID()), 201);
    assertEquals("TAKEN", result.get("status").asText());
    assertEquals("29", result.at("/balance/quantity").asText());
    assertEquals("-1", result.get("inventoryDelta").asText());
    assertEquals(1, actions(due));
    assertEquals(1, consumption(med));
    var first = day("2026-10-22").get("content").get(0);
    assertEquals("TAKEN", first.get("status").asText());
    assertEquals(result.get("actionId"), first.get("lastActionId"));
    assertEquals("2026-10-22T06:05:00Z", first.get("recordedAt").asText());
    assertEquals(1, first.get("version").asInt());
  }

  @Test
  void exactReplayReturnsOriginalEventAfterRestockWithoutAnotherDecrement() throws Exception {
    UUID key = UUID.randomUUID();
    var first = node(action(due, "TAKEN", key), 201);
    UUID adjustment = UUID.randomUUID();
    node(
        call(
            "POST",
            "/api/v1/inventory/" + med + "/adjustments",
            Map.of("reason", "RESTOCK", "quantity", "20", "clientOperationId", adjustment),
            adjustment,
            token),
        201);
    assertEquals(first, node(action(due, "TAKEN", key), 201));
    assertEquals("49", stock(med).at("/balance/quantity").asText());
    assertEquals(1, actions(due));
    assertEquals(1, consumption(med));
  }

  @Test
  void skippedDoesNotConsumeAndConflictingTransitionIs409() throws Exception {
    var skip = node(action(due, "SKIPPED", UUID.randomUUID()), 201);
    assertEquals("0", skip.get("inventoryDelta").asText());
    assertEquals("30", stock(med).at("/balance/quantity").asText());
    assertEquals(0, consumption(med));
    error(action(due, "TAKEN", UUID.randomUUID()), 409, "DOSE_STATE_CONFLICT");
    assertEquals(1, actions(due));
  }

  @Test
  void duplicateTakenWithDifferentKeyIsConflict() throws Exception {
    node(action(due, "TAKEN", UUID.randomUUID()), 201);
    error(action(due, "TAKEN", UUID.randomUUID()), 409, "DOSE_STATE_CONFLICT");
    assertEquals("29", stock(med).at("/balance/quantity").asText());
    assertEquals(1, consumption(med));
  }

  @Test
  void sameOperationIdCannotChangeActionOrOccurrence() throws Exception {
    UUID key = UUID.randomUUID();
    node(action(due, "TAKEN", key), 201);
    error(action(due, "SKIPPED", key), 409, "IDEMPOTENCY_KEY_REUSED");
    error(action(future, "TAKEN", key), 409, "IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void futureDoseAndFutureRecordedTimeAreRejected() throws Exception {
    error(action(future, "TAKEN", UUID.randomUUID()), 409, "DOSE_STATE_CONFLICT");
    UUID key = UUID.randomUUID();
    var input = body("TAKEN", key);
    input.put("recordedAt", "2026-10-23T00:00:00Z");
    error(
        call("POST", "/api/v1/doses/" + due + "/actions", input, key, token),
        422,
        "VALIDATION_ERROR");
    assertEquals(0, actions(due));
    assertEquals("30", stock(med).at("/balance/quantity").asText());
  }

  @Test
  void headerBodyValidationAndUnknownFieldsDoNotMutate() throws Exception {
    UUID key = UUID.randomUUID();
    var input = body("TAKEN", key);
    error(
        call("POST", "/api/v1/doses/" + due + "/actions", input, null, token),
        400,
        "INVALID_REQUEST");
    error(
        call("POST", "/api/v1/doses/" + due + "/actions", input, UUID.randomUUID(), token),
        422,
        "VALIDATION_ERROR");
    input.put("userId", UUID.randomUUID());
    error(
        call("POST", "/api/v1/doses/" + due + "/actions", input, key, token),
        400,
        "INVALID_REQUEST");
    assertEquals(0, actions(due));
  }

  @Test
  void optimisticVersionConflictDoesNotWrite() throws Exception {
    UUID key = UUID.randomUUID();
    var input = body("TAKEN", key);
    input.put("expectedVersion", 5);
    error(
        call("POST", "/api/v1/doses/" + due + "/actions", input, key, token),
        409,
        "DOSE_STATE_CONFLICT");
    assertEquals(0, actions(due));
    input.put("expectedVersion", 0);
    node(call("POST", "/api/v1/doses/" + due + "/actions", input, key, token), 201);
  }

  @Test
  void insufficientStockDoesNotBlockConfirmedTaken() throws Exception {
    UUID empty = createMedication("0");
    plan(empty, "0.5", List.of("09:00"));
    UUID id =
        UUID.fromString(
            jdbc.queryForObject(
                    "select d.id from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id where s.medication_id=? and d.local_date='2026-10-22'",
                    UUID.class,
                    empty)
                .toString());
    var result = node(action(id, "TAKEN", UUID.randomUUID()), 201);
    assertEquals("-0.5", result.at("/balance/quantity").asText());
    assertEquals("STOCK_NEGATIVE", result.at("/balance/warnings/0").asText());
  }

  @Test
  void unknownStockStaysUnknownButConsumptionIsAuditable() throws Exception {
    UUID unknown = createMedication(null);
    plan(unknown, "1", List.of("09:00"));
    UUID id =
        jdbc.queryForObject(
            "select d.id from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id where s.medication_id=? and d.local_date='2026-10-22'",
            UUID.class,
            unknown);
    var result = node(action(id, "TAKEN", UUID.randomUUID()), 201);
    assertFalse(result.at("/balance/known").asBoolean());
    assertTrue(result.at("/balance/quantity").isNull());
    assertEquals(1, consumption(unknown));
  }

  @Test
  void authOwnerAndReplayIsolationCoverActionsAndHistory() throws Exception {
    UUID key = UUID.randomUUID();
    node(action(due, "TAKEN", key), 201);
    error(
        call("POST", "/api/v1/doses/" + due + "/actions", body("TAKEN", key), key, null),
        401,
        "UNAUTHORIZED");
    token = login();
    error(action(due, "TAKEN", key), 404, "NOT_FOUND");
    error(call("GET", "/api/v1/doses/" + due + "/actions", null, null, token), 404, "NOT_FOUND");
    assertEquals(1, actions(due));
  }

  @Test
  void concurrentExactReplaysProduceOneSemanticResult() throws Exception {
    UUID key = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<HttpResponse<String>> request =
          () -> {
            gate.await();
            return action(due, "TAKEN", key);
          };
      var a = pool.submit(request);
      var b = pool.submit(request);
      gate.countDown();
      assertEquals(node(a.get(15, TimeUnit.SECONDS), 201), node(b.get(15, TimeUnit.SECONDS), 201));
    }
    assertEquals(1, actions(due));
    assertEquals(1, consumption(med));
    assertEquals("29", stock(med).at("/balance/quantity").asText());
  }

  @Test
  void concurrentDifferentKeysCannotDoubleConsumeOneDose() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<HttpResponse<String>> request =
          () -> {
            gate.await();
            return action(due, "TAKEN", UUID.randomUUID());
          };
      var a = pool.submit(request);
      var b = pool.submit(request);
      gate.countDown();
      assertEquals(
          List.of(201, 409),
          List.of(
                  a.get(15, TimeUnit.SECONDS).statusCode(),
                  b.get(15, TimeUnit.SECONDS).statusCode())
              .stream()
              .sorted()
              .toList());
    }
    assertEquals(1, actions(due));
    assertEquals(1, consumption(med));
  }

  @Test
  void historyIsImmutableAndSurvivesScheduleRevision() throws Exception {
    var result = node(action(due, "TAKEN", UUID.randomUUID()), 201);
    node(
        call(
            "POST",
            "/api/v1/medications/" + med + "/schedules",
            Map.of(
                "times",
                List.of("10:00"),
                "quantityPerDose",
                "2",
                "timezone",
                "Europe/Istanbul",
                "startDate",
                "2026-10-23"),
            null,
            token),
        201);
    var history = node(call("GET", "/api/v1/doses/" + due + "/actions", null, null, token), 200);
    assertEquals(result, history.get("content").get(0));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("update dose_actions set action='SKIPPED' where occurrence_id=?", due));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("delete from dose_actions where occurrence_id=?", due));
    jdbc.update("delete from users where id=?", owner);
    assertEquals(0, actions(due));
    assertEquals(0, consumption(med));
  }

  @Test
  void completionFailureRollsBackActionLedgerSnapshotAndOccurrence() throws Exception {
    UUID key = UUID.randomUUID();
    jdbc.execute(
        "create function test_fail_action() returns trigger language plpgsql as $$ begin raise exception 'Synthetic completion failure' using errcode='23514'; end; $$");
    jdbc.execute(
        "create trigger test_fail_action before update on operation_registry for each row when (new.id='"
            + key
            + "'::uuid) execute function test_fail_action()");
    try {
      error(action(due, "TAKEN", key), 409, "RESOURCE_CONFLICT");
      assertEquals(0, actions(due));
      assertEquals(0, consumption(med));
      assertEquals("30", stock(med).at("/balance/quantity").asText());
      assertEquals(
          "PENDING",
          jdbc.queryForObject("select status from dose_occurrences where id=?", String.class, due));
      assertEquals(
          0,
          jdbc.queryForObject("select version from dose_occurrences where id=?", Long.class, due));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from operation_registry where id=?", Integer.class, key));
    } finally {
      jdbc.execute("drop trigger test_fail_action on operation_registry");
      jdbc.execute("drop function test_fail_action()");
    }
  }

  @Test
  void fixedPlanProjectionUsesFutureSlotsAndIsExplicitlyEstimated() throws Exception {
    var projected = stock(med).get("projection");
    assertEquals("ESTIMATED", projected.get("status").asText());
    assertEquals("2", projected.get("dailyConsumption").asText());
    assertEquals("15", projected.get("fullDays").asText());
    assertEquals("2026-11-06T06:00:00Z", projected.get("estimatedDepletionAt").asText());
  }

  @Test
  void variableAndFinitePlansDoNotGiveMisleadingForecasts() throws Exception {
    node(
        call(
            "POST",
            "/api/v1/medications/" + med + "/schedules",
            Map.of(
                "times",
                List.of("10:00"),
                "quantityPerDose",
                "2",
                "timezone",
                "Europe/Istanbul",
                "startDate",
                "2026-10-23"),
            null,
            token),
        201);
    assertEquals("VARIABLE_SCHEDULE", stock(med).at("/projection/reason").asText());
    UUID finite = createMedication("30");
    node(
        call(
            "POST",
            "/api/v1/medications/" + finite + "/schedules",
            Map.of(
                "times",
                List.of("21:00"),
                "quantityPerDose",
                "1",
                "timezone",
                "Europe/Istanbul",
                "startDate",
                "2026-10-22",
                "endDate",
                "2026-10-22"),
            null,
            token),
        201);
    assertEquals("PLAN_ENDS_BEFORE_DEPLETION", stock(finite).at("/projection/reason").asText());
  }

  private Map<String, Object> undoBody(UUID key, UUID action) {
    var input = body("UNDO", key);
    input.put("undoActionId", action);
    return input;
  }

  private JsonNode undo(UUID action, UUID key) throws Exception {
    return node(
        call("POST", "/api/v1/doses/" + due + "/actions", undoBody(key, action), key, token), 201);
  }

  @Test
  void undoTakenRestoresStockThroughOneCompensatingEntry() throws Exception {
    var taken = node(action(due, "TAKEN", UUID.randomUUID()), 201);
    UUID target = UUID.fromString(taken.get("actionId").asText()), key = UUID.randomUUID();
    var reversed = undo(target, key);
    assertEquals("DUE", reversed.get("status").asText());
    assertEquals("1", reversed.get("inventoryDelta").asText());
    assertEquals("30", stock(med).at("/balance/quantity").asText());
    assertEquals(2, actions(due));
    assertEquals(1, consumption(med));
    assertEquals(reversed, undo(target, key));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from inventory_ledger where medication_id=? and reason='UNDO'",
            Integer.class,
            med));
    var history = node(call("GET", "/api/v1/doses/" + due + "/actions", null, null, token), 200);
    assertEquals(taken, history.at("/content/1"));
  }

  @Test
  void undoSkippedDoesNotChangeStockAndCanThenBeTaken() throws Exception {
    var skip = node(action(due, "SKIPPED", UUID.randomUUID()), 201);
    var reversed = undo(UUID.fromString(skip.get("actionId").asText()), UUID.randomUUID());
    assertEquals("0", reversed.get("inventoryDelta").asText());
    assertEquals("30", stock(med).at("/balance/quantity").asText());
    assertEquals(0, consumption(med));
    node(action(due, "TAKEN", UUID.randomUUID()), 201);
    assertEquals("29", stock(med).at("/balance/quantity").asText());
  }

  @Test
  void staleUndoCannotReverseANewerTakenAndReplayCannotDoubleRefund() throws Exception {
    var first = node(action(due, "TAKEN", UUID.randomUUID()), 201);
    UUID target = UUID.fromString(first.get("actionId").asText()), key = UUID.randomUUID();
    var reversed = undo(target, key);
    node(action(due, "TAKEN", UUID.randomUUID()), 201);
    UUID stale = UUID.randomUUID();
    error(
        call("POST", "/api/v1/doses/" + due + "/actions", undoBody(stale, target), stale, token),
        409,
        "DOSE_STATE_CONFLICT");
    assertEquals(reversed, undo(target, key));
    assertEquals("29", stock(med).at("/balance/quantity").asText());
    assertEquals(3, actions(due));
  }

  @Test
  void undoRequiresTheObservedLatestActionAndCannotUndoAnUndo() throws Exception {
    UUID key = UUID.randomUUID();
    error(action(due, "UNDO", key), 422, "VALIDATION_ERROR");
    var taken = node(action(due, "TAKEN", UUID.randomUUID()), 201);
    var reversed = undo(UUID.fromString(taken.get("actionId").asText()), UUID.randomUUID());
    UUID again = UUID.randomUUID();
    error(
        call(
            "POST",
            "/api/v1/doses/" + due + "/actions",
            undoBody(again, UUID.fromString(reversed.get("actionId").asText())),
            again,
            token),
        409,
        "DOSE_STATE_CONFLICT");
  }

  @Test
  void parallelUndoWithDifferentKeysRefundsOnlyOnce() throws Exception {
    UUID target =
        UUID.fromString(
            node(action(due, "TAKEN", UUID.randomUUID()), 201).get("actionId").asText());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<HttpResponse<String>> request =
          () -> {
            gate.await();
            UUID key = UUID.randomUUID();
            return call(
                "POST", "/api/v1/doses/" + due + "/actions", undoBody(key, target), key, token);
          };
      var a = pool.submit(request);
      var b = pool.submit(request);
      gate.countDown();
      assertEquals(
          List.of(201, 409),
          List.of(
                  a.get(15, TimeUnit.SECONDS).statusCode(),
                  b.get(15, TimeUnit.SECONDS).statusCode())
              .stream()
              .sorted()
              .toList());
    }
    assertEquals("30", stock(med).at("/balance/quantity").asText());
    assertEquals(2, actions(due));
  }

  @Test
  void undoAfterCorrectionAddsOnlyTheOriginalConsumptionAndErasureCascades() throws Exception {
    UUID target =
        UUID.fromString(
            node(action(due, "TAKEN", UUID.randomUUID()), 201).get("actionId").asText());
    UUID key = UUID.randomUUID();
    node(
        call(
            "POST",
            "/api/v1/inventory/" + med + "/adjustments",
            Map.of("reason", "CORRECTION", "quantity", "5", "clientOperationId", key),
            key,
            token),
        201);
    undo(target, UUID.randomUUID());
    assertEquals("6", stock(med).at("/balance/quantity").asText());
    jdbc.update("delete from users where id=?", owner);
    assertEquals(0, actions(due));
    assertEquals(0, consumption(med));
  }

  @Test
  void weeklySummarySeparatesSkippedFromUnconfirmedAndCountsOnlyOwner() throws Exception {
    node(action(due, "SKIPPED", UUID.randomUUID()), 201);
    var summary =
        node(call("GET", "/api/v1/doses/summary?weekStart=2026-10-19", null, null, token), 200);
    assertEquals(12, summary.get("scheduledDoses").asLong());
    assertEquals(1, summary.get("skipped").asLong());
    assertEquals(4, summary.get("missed").asLong());
    assertEquals(4, summary.get("unconfirmedAtDeadline").asLong());
    assertEquals(7, summary.get("pending").asLong());
    assertEquals("30", stock(med).at("/balance/quantity").asText());
    token = login();
    var other = node(call("GET", "/api/v1/doses/summary", null, null, token), 200);
    assertEquals(0, other.get("scheduledDoses").asLong());
    error(
        call("GET", "/api/v1/doses/summary?weekStart=2026-10-20", null, null, token),
        422,
        "VALIDATION_ERROR");
    error(
        call("GET", "/api/v1/doses/summary?weekStart=2026-10-26", null, null, token),
        422,
        "VALIDATION_ERROR");
    error(call("GET", "/api/v1/doses/summary", null, null, null), 401, "UNAUTHORIZED");
  }

  @Test
  void lateConfirmationAndUndoKeepTheOriginalUnconfirmedDeadline() throws Exception {
    node(call("GET", "/api/v1/doses/summary", null, null, token), 200);
    UUID past =
        jdbc.queryForObject(
            "select d.id from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id where s.medication_id=? and d.local_date='2026-10-20' order by d.due_at_utc limit 1",
            UUID.class,
            med);
    UUID key = UUID.randomUUID();
    var taken = node(action(past, "TAKEN", key), 201);
    due = past;
    var reversed = undo(UUID.fromString(taken.get("actionId").asText()), UUID.randomUUID());
    assertEquals("MISSED", reversed.get("status").asText());
    node(action(past, "TAKEN", UUID.randomUUID()), 201);
    var summary = node(call("GET", "/api/v1/doses/summary", null, null, token), 200);
    assertEquals(1, summary.get("taken").asLong());
    assertEquals(3, summary.get("missed").asLong());
    assertEquals(4, summary.get("unconfirmedAtDeadline").asLong());
  }

  @Test
  void unitChangesRequireSeparateMedicationOnceStockOrPlanExists() throws Exception {
    error(
        call("PATCH", "/api/v1/medications/" + med, Map.of("unit", "ml"), null, token),
        409,
        "MEDICATION_UNIT_LOCKED");
    node(call("PATCH", "/api/v1/medications/" + med, Map.of("unit", "tablet"), null, token), 200);
    UUID empty = createMedication(null);
    node(
        call("PATCH", "/api/v1/medications/" + empty, Map.of("unit", "capsule"), null, token), 200);
    plan(empty, "1", List.of("09:00"));
    error(
        call("PATCH", "/api/v1/medications/" + empty, Map.of("unit", "ml"), null, token),
        409,
        "MEDICATION_UNIT_LOCKED");
    UUID restocked = createMedication(null), key = UUID.randomUUID();
    node(
        call(
            "POST",
            "/api/v1/inventory/" + restocked + "/adjustments",
            Map.of("reason", "RESTOCK", "quantity", "5", "clientOperationId", key),
            key,
            token),
        201);
    error(
        call("PATCH", "/api/v1/medications/" + restocked, Map.of("unit", "ml"), null, token),
        409,
        "MEDICATION_UNIT_LOCKED");
  }

  @Test
  void fractionalAndDstStockForecastsKeepDecimalAndTimezonePrecision() throws Exception {
    UUID small = createMedication("0.3");
    plan(small, "0.1", List.of("21:00"));
    assertEquals(
        "2026-10-24T18:00:00Z", stock(small).at("/projection/estimatedDepletionAt").asText());
    UUID dst = createMedication("1");
    node(
        call(
            "POST",
            "/api/v1/medications/" + dst + "/schedules",
            Map.of(
                "times",
                List.of("02:30"),
                "quantityPerDose",
                "1",
                "timezone",
                "Europe/Berlin",
                "startDate",
                "2026-10-25"),
            null,
            token),
        201);
    assertEquals(
        "2026-10-25T00:30:00Z", stock(dst).at("/projection/estimatedDepletionAt").asText());
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-22T12:00:00.123456789Z"), ZoneOffset.UTC);
    }
  }
}
