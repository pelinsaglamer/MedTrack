package com.medtrack.doses;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class DoseActionStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public DoseActionStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public record Occurrence(
      UUID id,
      UUID medicationId,
      Instant dueAt,
      String timezone,
      LocalDate localDate,
      String unit,
      String status,
      BigDecimal quantity,
      long version) {}

  public record Previous(UUID id, String action, String before) {}

  public Occurrence owned(UUID id, UUID owner) {
    var rows =
        jdbc.query(
            "select d.*,s.medication_id,s.quantity_per_dose,s.timezone from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id join medications m on m.id=s.medication_id where d.id=? and m.user_id=?",
            (rs, n) ->
                new Occurrence(
                    rs.getObject("id", UUID.class),
                    rs.getObject("medication_id", UUID.class),
                    rs.getTimestamp("due_at_utc").toInstant(),
                    rs.getString("timezone"),
                    rs.getObject("local_date", LocalDate.class),
                    rs.getString("unit"),
                    rs.getString("status"),
                    rs.getBigDecimal("quantity_per_dose"),
                    rs.getLong("version")),
            id,
            owner);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Previous latest(UUID occurrence) {
    var rows =
        jdbc.query(
            "select id,action,status_before from dose_actions where occurrence_id=? order by occurrence_version desc limit 1",
            (rs, n) ->
                new Previous(
                    rs.getObject("id", UUID.class),
                    rs.getString("action"),
                    rs.getString("status_before")),
            occurrence);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void append(
      UUID id,
      Occurrence occurrence,
      UUID operation,
      String action,
      UUID undoOf,
      String before,
      String after,
      Instant recordedAt,
      Instant now) {
    jdbc.update(
        "insert into dose_actions(id,medication_id,occurrence_id,client_operation_id,action,undo_of,status_before,status_after,recorded_at,created_at,occurrence_version) values (?,?,?,?,?,?,?,?,?,?,?)",
        id,
        occurrence.medicationId(),
        occurrence.id(),
        operation,
        action,
        undoOf,
        before,
        after,
        Timestamp.from(recordedAt),
        Timestamp.from(now),
        occurrence.version() + 1);
  }

  public boolean transition(Occurrence occurrence, String status) {
    return jdbc.update(
            "update dose_occurrences set status=?,version=version+1 where id=? and version=?",
            status,
            occurrence.id(),
            occurrence.version())
        == 1;
  }

  public void retainMissedAt(UUID id, Instant deadline) {
    if (deadline != null)
      jdbc.update(
          "update dose_occurrences set missed_at=coalesce(missed_at,?) where id=?",
          Timestamp.from(deadline),
          id);
  }

  public DoseActionDtos.History history(UUID occurrence, int page, int size) {
    long count =
        jdbc.queryForObject(
            "select count(*) from dose_actions where occurrence_id=?", Long.class, occurrence);
    var rows =
        jdbc.query(
            "select r.response_json from dose_actions a join operation_registry r on r.id=a.client_operation_id where a.occurrence_id=? order by a.occurrence_version desc limit ? offset ?",
            (rs, n) -> json.readValue(rs.getString("response_json"), DoseActionDtos.Result.class),
            occurrence,
            size,
            (long) page * size);
    return new DoseActionDtos.History(rows, page, size, count, (int) ((count + size - 1) / size));
  }
}
