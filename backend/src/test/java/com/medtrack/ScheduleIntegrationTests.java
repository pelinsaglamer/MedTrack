package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.auth.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
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
@Import(ScheduleIntegrationTests.TimeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ScheduleIntegrationTests {
  @LocalServerPort int port;
  @Autowired AuthService auth;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;
  String token;
  UUID owner;
  String email;
  final HttpClient http = HttpClient.newHttpClient();
  final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setup() {
    clock.now = Instant.parse("2026-10-22T09:00:00.123456789Z");
    token = login("Europe/Istanbul");
  }

  private String login(String timezone) {
    email = UUID.randomUUID() + "@example.invalid";
    owner = auth.register(new AuthDtos.Register(email, "fictional-password-123", timezone)).id();
    return auth.login(new AuthDtos.Login(email, "fictional-password-123")).accessToken();
  }

  private HttpResponse<String> call(String method, String path, Object input, String bearer)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json");
    if (bearer != null) b.header("Authorization", "Bearer " + bearer);
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
    assertEquals(
        status,
        response.statusCode(),
        () -> "HTTP " + response.statusCode() + ": " + response.body());
    return json.readTree(response.body());
  }

  private void error(HttpResponse<String> response, int status, String code) throws Exception {
    var b = node(response, status);
    assertEquals(code, b.get("code").asText());
    assertEquals(
        response.headers().firstValue("X-Request-ID").orElseThrow(), b.get("requestId").asText());
    assertTrue(b.get("fieldErrors").isObject());
  }

  private String medication() throws Exception {
    return node(
            call(
                "POST",
                "/api/v1/medications",
                Map.of("name", "Fictional Scheduled", "unit", "tablet", "initialStock", "30"),
                token),
            201)
        .get("id")
        .asText();
  }

  private Map<String, Object> plan(String start, String end, String zone, List<String> times) {
    Map<String, Object> p = new HashMap<>();
    p.put("times", times);
    p.put("quantityPerDose", "1.25");
    p.put("timezone", zone);
    p.put("startDate", start);
    if (end != null) p.put("endDate", end);
    return p;
  }

  private JsonNode schedule(String med, Map<String, Object> plan) throws Exception {
    return node(call("POST", "/api/v1/medications/" + med + "/schedules", plan, token), 201);
  }

  private JsonNode day(String date) throws Exception {
    return node(call("GET", "/api/v1/doses?date=" + date + "&size=100", null, token), 200);
  }

  private Set<String> ids(JsonNode page) {
    Set<String> ids = new HashSet<>();
    page.get("content").forEach(n -> ids.add(n.get("id").asText()));
    return ids;
  }

  private int count(String med) {
    return jdbc.queryForObject(
        "select count(*) from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id where s.medication_id=?",
        Integer.class,
        UUID.fromString(med));
  }

  @Test
  void twoDailyOccurrencesHaveCorrectStatusQuantityAndStableIdentity() throws Exception {
    String med = medication();
    var version =
        schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("21:00", "09:00")));
    assertEquals(1, version.get("version").asInt());
    assertEquals("09:00", version.get("times").get(0).asText());
    assertEquals(28, count(med));
    var today = day("2026-10-22");
    assertEquals(2, today.get("totalElements").asInt());
    var first = today.get("content").get(0);
    var second = today.get("content").get(1);
    assertEquals("2026-10-22T06:00:00Z", first.get("dueAtUtc").asText());
    assertEquals("DUE", first.get("status").asText());
    assertEquals("PENDING", second.get("status").asText());
    assertEquals("1.25", first.get("quantityPerDose").asText());
    assertEquals(ids(today), ids(day("2026-10-22")));
    assertEquals(28, count(med));
  }

  @Test
  void startEndDatesAreInclusiveAndFinite() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-23", "2026-10-24", "Europe/Istanbul", List.of("09:00", "21:00")));
    assertEquals(0, day("2026-10-22").get("totalElements").asInt());
    assertEquals(2, day("2026-10-23").get("totalElements").asInt());
    assertEquals(2, day("2026-10-24").get("totalElements").asInt());
    assertEquals(0, day("2026-10-25").get("totalElements").asInt());
    assertEquals(4, count(med));
  }

  @Test
  void tomorrowRevisionPreservesYesterdayAndTodayRecords() throws Exception {
    String med = medication();
    var old = schedule(med, plan("2026-10-20", null, "Europe/Istanbul", List.of("09:00", "21:00")));
    var yesterday = day("2026-10-21");
    var today = day("2026-10-22");
    var future = day("2026-10-23");
    var next = plan("2026-10-23", null, "Europe/Istanbul", List.of("10:00"));
    next.put("quantityPerDose", "0.5");
    var v2 = schedule(med, next);
    assertEquals(2, v2.get("version").asInt());
    assertEquals(yesterday, day("2026-10-21"));
    assertEquals(today, day("2026-10-22"));
    var updated = day("2026-10-23");
    assertEquals(1, updated.get("totalElements").asInt());
    assertTrue(Collections.disjoint(ids(future), ids(updated)));
    assertEquals("0.5", updated.get("content").get(0).get("quantityPerDose").asText());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from schedule_versions where medication_id=?",
            Integer.class,
            UUID.fromString(med)));
  }

  @Test
  void sameDayRevisionPreservesPastAndReplacesFuture() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00", "21:00")));
    var before = day("2026-10-22");
    String pastId = before.get("content").get(0).get("id").asText();
    String futureId = before.get("content").get(1).get("id").asText();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("13:00")));
    var after = day("2026-10-22");
    assertEquals(2, after.get("totalElements").asInt());
    assertTrue(ids(after).contains(pastId));
    assertFalse(ids(after).contains(futureId));
    assertEquals("13:00", after.get("content").get(1).get("localTime").asText());
  }

  @Test
  void revisionAtExactDoseInstantDoesNotDuplicateIt() throws Exception {
    clock.now = Instant.parse("2026-10-22T06:00:00Z");
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00")));
    var before = day("2026-10-22");
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00", "21:00")));
    var after = day("2026-10-22");
    assertEquals(2, after.get("totalElements").asInt());
    assertEquals(before.get("content").get(0), after.get("content").get(0));
  }

  @Test
  void queuedFutureVersionsCanBeReplacedWithoutOverlap() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00")));
    schedule(med, plan("2026-10-25", null, "Europe/Istanbul", List.of("10:00")));
    var finalVersion = schedule(med, plan("2026-10-24", null, "Europe/Istanbul", List.of("11:00")));
    assertEquals(3, finalVersion.get("version").asInt());
    assertEquals("11:00", day("2026-10-25").get("content").get(0).get("localTime").asText());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from schedule_versions a join schedule_versions b on a.medication_id=b.medication_id and a.version<b.version where a.medication_id=? and tstzrange(a.effective_from,a.effective_until,'[)') && tstzrange(b.effective_from,b.effective_until,'[)')",
            Integer.class,
            UUID.fromString(med)));
  }

  @Test
  void invalidSchedulesAreRejected() throws Exception {
    String med = medication();
    String path = "/api/v1/medications/" + med + "/schedules";
    var base = plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00"));
    for (var change :
        List.of(
            Map.of("times", List.of("09:00", "09:00")),
            Map.of("times", List.of("9:00")),
            Map.of("times", List.of("24:00")),
            Map.of("times", List.of("09:00:30")),
            Map.of("times", List.of()),
            Map.of("quantityPerDose", "0"),
            Map.of("quantityPerDose", "-1"),
            Map.of("quantityPerDose", "0.0000001"),
            Map.of("timezone", "+03:00"),
            Map.of("endDate", "2026-10-21"))) {
      Map<String, Object> input = new HashMap<>(base);
      input.putAll(change);
      error(call("POST", path, input, token), 422, "VALIDATION_ERROR");
    }
    var spoof = new HashMap<>(base);
    spoof.put("userId", UUID.randomUUID().toString());
    error(call("POST", path, spoof, token), 400, "INVALID_REQUEST");
    assertEquals(0, count(med));
  }

  @Test
  void pastRevisionsAndArchivedMedicationAreRejected() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-20", null, "Europe/Istanbul", List.of("09:00")));
    error(
        call(
            "POST",
            "/api/v1/medications/" + med + "/schedules",
            plan("2026-10-21", null, "Europe/Istanbul", List.of("10:00")),
            token),
        422,
        "VALIDATION_ERROR");
    node(call("PATCH", "/api/v1/medications/" + med, Map.of("archived", true), token), 200);
    error(
        call(
            "POST",
            "/api/v1/medications/" + med + "/schedules",
            plan("2026-10-23", null, "Europe/Istanbul", List.of("10:00")),
            token),
        409,
        "SCHEDULE_CONFLICT");
  }

  @Test
  void archiveKeepsPastRemovesFutureAndDoesNotRegenerateIt() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00", "21:00")));
    var past = day("2026-10-22").get("content").get(0);
    node(call("PATCH", "/api/v1/medications/" + med, Map.of("archived", true), token), 200);
    var after = day("2026-10-22");
    assertEquals(1, after.get("totalElements").asInt());
    assertEquals(past, after.get("content").get(0));
    assertEquals(0, day("2026-10-23").get("totalElements").asInt());
    assertEquals(1, count(med));
  }

  @Test
  void ownerIsolationAndAuthenticationCoverScheduleAndDoses() throws Exception {
    String med = medication();
    var input = plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00"));
    schedule(med, input);
    error(
        call("POST", "/api/v1/medications/" + med + "/schedules", input, null),
        401,
        "UNAUTHORIZED");
    error(call("GET", "/api/v1/doses", null, null), 401, "UNAUTHORIZED");
    token = login("Europe/Istanbul");
    error(
        call("POST", "/api/v1/medications/" + med + "/schedules", input, token), 404, "NOT_FOUND");
    assertEquals(0, day("2026-10-22").get("totalElements").asInt());
  }

  @Test
  void accountDayBoundariesDifferFromIntendedScheduleDate() throws Exception {
    token = login("America/Los_Angeles");
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("00:30")));
    var page = day("2026-10-21");
    assertEquals(1, page.get("totalElements").asInt());
    assertEquals("America/Los_Angeles", page.get("timezone").asText());
    assertEquals("2026-10-22", page.get("content").get(0).get("localDate").asText());
  }

  @Test
  void springAndAutumnDstGenerateOneStableSlot() throws Exception {
    clock.now = Instant.parse("2026-03-28T12:00:00Z");
    token = login("Europe/Berlin");
    String med = medication();
    schedule(med, plan("2026-03-29", "2026-03-29", "Europe/Berlin", List.of("02:30")));
    var spring = day("2026-03-29");
    assertEquals(1, spring.get("totalElements").asInt());
    assertEquals("2026-03-29T01:30:00Z", spring.get("content").get(0).get("dueAtUtc").asText());
    assertEquals("02:30", spring.get("content").get(0).get("localTime").asText());
    clock.now = Instant.parse("2026-10-24T12:00:00Z");
    token = login("Europe/Berlin");
    med = medication();
    schedule(med, plan("2026-10-25", "2026-10-25", "Europe/Berlin", List.of("02:30")));
    var autumn = day("2026-10-25");
    assertEquals(1, autumn.get("totalElements").asInt());
    assertEquals("2026-10-25T00:30:00Z", autumn.get("content").get(0).get("dueAtUtc").asText());
    assertEquals(ids(autumn), ids(day("2026-10-25")));
  }

  @Test
  void rollingHorizonExtendsAndHistoryDoesNotInventTakenOrMissed() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00")));
    assertEquals(14, count(med));
    clock.now = clock.now.plus(Duration.ofDays(2));
    token = auth.login(new AuthDtos.Login(email, "fictional-password-123")).accessToken();
    assertEquals(1, day("2026-11-06").get("totalElements").asInt());
    assertEquals(16, count(med));
    assertEquals("DUE", day("2026-10-22").get("content").get(0).get("status").asText());
  }

  @Test
  void paginationHorizonAndSwaggerAreDocumented() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00", "21:00")));
    var page = node(call("GET", "/api/v1/doses?date=2026-10-22&size=1&page=1", null, token), 200);
    assertEquals(2, page.get("totalElements").asInt());
    assertEquals(1, page.get("content").size());
    for (String q : List.of("size=101", "page=-1", "date=2026-11-05"))
      error(call("GET", "/api/v1/doses?" + q, null, token), 422, "VALIDATION_ERROR");
    var spec = node(call("GET", "/v3/api-docs", null, null), 200);
    assertTrue(spec.get("paths").has("/api/v1/doses"));
    assertTrue(spec.get("paths").has("/api/v1/medications/{medicationId}/schedules"));
  }

  @Test
  void concurrentGenerationAndRevisionsDoNotDuplicateOrOverlap() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00")));
    String access = token;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<HttpResponse<String>> action =
          () -> {
            gate.await();
            return call(
                "POST",
                "/api/v1/medications/" + med + "/schedules",
                plan("2026-10-23", null, "Europe/Istanbul", List.of("10:00")),
                access);
          };
      var a = pool.submit(action);
      var b = pool.submit(action);
      gate.countDown();
      node(a.get(15, TimeUnit.SECONDS), 201);
      node(b.get(15, TimeUnit.SECONDS), 201);
      var readA = pool.submit(() -> call("GET", "/api/v1/doses?date=2026-10-23", null, access));
      var readB = pool.submit(() -> call("GET", "/api/v1/doses?date=2026-10-23", null, access));
      assertEquals(
          ids(node(readA.get(15, TimeUnit.SECONDS), 200)),
          ids(node(readB.get(15, TimeUnit.SECONDS), 200)));
    }
    assertEquals(1, day("2026-10-23").get("totalElements").asInt());
  }

  @Test
  void futureConfirmedRecordBlocksRewriteAndRemainsUntouched() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("21:00")));
    var dose = day("2026-10-23").get("content").get(0);
    jdbc.update(
        "update dose_occurrences set status='TAKEN' where id=?",
        UUID.fromString(dose.get("id").asText()));
    error(
        call(
            "POST",
            "/api/v1/medications/" + med + "/schedules",
            plan("2026-10-23", null, "Europe/Istanbul", List.of("10:00")),
            token),
        409,
        "SCHEDULE_CONFLICT");
    assertEquals(dose.get("id"), day("2026-10-23").get("content").get(0).get("id"));
    assertEquals("TAKEN", day("2026-10-23").get("content").get(0).get("status").asText());
  }

  @Test
  void snapshotsAndLogsKeepExistingDoseHistorySafe(CapturedOutput output) throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00")));
    var before = day("2026-10-22");
    String privateName = "Fictional Private " + UUID.randomUUID();
    node(
        call(
            "PATCH",
            "/api/v1/medications/" + med,
            Map.of("name", privateName, "unit", "capsule"),
            token),
        200);
    assertEquals(before, day("2026-10-22"));
    assertFalse(output.getAll().contains(privateName));
  }

  @Test
  void databaseRejectsOverlappingVersionsAndDuplicateOccurrences() throws Exception {
    String med = medication();
    schedule(med, plan("2026-10-22", null, "Europe/Istanbul", List.of("09:00")));
    var overlap =
        assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () ->
                jdbc.update(
                    "insert into schedule_versions(id,medication_id,version,quantity_per_dose,timezone,start_date,end_date,effective_from,effective_until,created_at) select ?,medication_id,99,quantity_per_dose,timezone,start_date,end_date,effective_from,effective_until,created_at from schedule_versions where medication_id=? and version=1",
                    UUID.randomUUID(),
                    UUID.fromString(med)));
    assertEquals("23P01", ((java.sql.SQLException) overlap.getMostSpecificCause()).getSQLState());
    var duplicate =
        assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () ->
                jdbc.update(
                    "insert into dose_occurrences(id,schedule_version_id,schedule_time_id,due_at_utc,local_date,local_time,status,medication_name,unit) select ?,d.schedule_version_id,d.schedule_time_id,d.due_at_utc,d.local_date,d.local_time,d.status,d.medication_name,d.unit from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id where s.medication_id=? limit 1",
                    UUID.randomUUID(),
                    UUID.fromString(med)));
    assertEquals("23505", ((java.sql.SQLException) duplicate.getMostSpecificCause()).getSQLState());
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant now = Instant.parse("2026-10-22T09:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
