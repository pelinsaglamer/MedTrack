package com.medtrack.doses;

import com.medtrack.medications.Medication;
import com.medtrack.schedules.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DoseStore {
  private final JdbcTemplate jdbc;

  public DoseStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      ScheduleVersion version,
      ScheduleTime slot,
      Medication medication,
      LocalDate localDate,
      Instant due) {
    // Deterministic identity survives concurrent generation and future regeneration of the same
    // slot.
    UUID id =
        UUID.nameUUIDFromBytes(
            (version.id() + "/" + localDate + "/" + slot.id())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    jdbc.update(
        "insert into dose_occurrences(id,schedule_version_id,schedule_time_id,due_at_utc,local_date,local_time,medication_name,unit) values (?,?,?,?,?,?,?,?) on conflict do nothing",
        id,
        version.id(),
        slot.id(),
        Timestamp.from(due),
        localDate,
        slot.time(),
        medication.name(),
        medication.unit());
  }

  public void deleteFuturePending(UUID medicationId, Instant boundary, Instant now) {
    jdbc.update(
        "delete from dose_occurrences d using schedule_versions s where d.schedule_version_id=s.id and s.medication_id=? and d.due_at_utc>=? and d.due_at_utc>? and d.status='PENDING'",
        medicationId,
        Timestamp.from(boundary),
        Timestamp.from(now));
  }

  public boolean hasFutureFinalized(UUID medicationId, Instant boundary, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id where s.medication_id=? and d.due_at_utc>=? and d.due_at_utc>? and d.status<>'PENDING')",
            Boolean.class,
            medicationId,
            Timestamp.from(boundary),
            Timestamp.from(now)));
  }

  public DoseDtos.WeeklySummary summary(UUID owner, LocalDate start, ZoneId zone, Instant now) {
    Instant from = start.atStartOfDay(zone).toInstant(),
        until = start.plusDays(7).atStartOfDay(zone).toInstant();
    return jdbc.queryForObject(
        "select count(*) filter(where d.missed_at is not null) unconfirmed_deadline, count(*) total, count(*) filter(where d.status='TAKEN') taken, count(*) filter(where d.status='SKIPPED') skipped, count(*) filter(where d.status='MISSED') missed, count(*) filter(where d.status in ('PENDING','DUE') and d.due_at_utc<=?) awaiting, count(*) filter(where d.status in ('PENDING','DUE') and d.due_at_utc>?) pending from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id join medications m on m.id=s.medication_id where m.user_id=? and d.due_at_utc>=? and d.due_at_utc<?",
        (rs, n) ->
            new DoseDtos.WeeklySummary(
                start,
                start.plusDays(7),
                zone.getId(),
                rs.getLong("total"),
                rs.getLong("taken"),
                rs.getLong("skipped"),
                rs.getLong("missed"),
                rs.getLong("awaiting"),
                rs.getLong("pending"),
                rs.getLong("unconfirmed_deadline")),
        Timestamp.from(now),
        Timestamp.from(now),
        owner,
        Timestamp.from(from),
        Timestamp.from(until));
  }

  public DoseDtos.Page list(
      UUID owner, LocalDate date, ZoneId zone, int page, int size, Instant now) {
    Instant from = date.atStartOfDay(zone).toInstant(),
        until = date.plusDays(1).atStartOfDay(zone).toInstant();
    String joins =
        " from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id join medications m on m.id=s.medication_id where m.user_id=? and d.due_at_utc>=? and d.due_at_utc<?";
    long count =
        jdbc.queryForObject(
            "select count(*)" + joins,
            Long.class,
            owner,
            Timestamp.from(from),
            Timestamp.from(until));
    var rows =
        jdbc.query(
            "select d.*,s.medication_id,s.version as schedule_version_number,s.quantity_per_dose,s.timezone, (select a.id from dose_actions a where a.occurrence_id=d.id order by a.occurrence_version desc limit 1) as last_action_id, (select a.recorded_at from dose_actions a where a.occurrence_id=d.id order by a.occurrence_version desc limit 1) as action_recorded_at"
                + joins
                + " order by d.due_at_utc,d.id limit ? offset ?",
            (rs, n) -> view(rs, now),
            owner,
            Timestamp.from(from),
            Timestamp.from(until),
            size,
            (long) page * size);
    return new DoseDtos.Page(
        rows, date, zone.getId(), page, size, count, (int) ((count + size - 1) / size));
  }

  public Optional<UUID> ownedMedicationId(UUID id, UUID owner) {
    return jdbc
        .query(
            "select s.medication_id from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id join medications m on m.id=s.medication_id where d.id=? and m.user_id=?",
            (rs, n) -> rs.getObject(1, UUID.class),
            id,
            owner)
        .stream()
        .findFirst();
  }

  public Optional<DoseDtos.View> get(UUID id, UUID owner, Instant now) {
    return jdbc
        .query(
            "select d.*,s.medication_id,s.version as schedule_version_number,s.quantity_per_dose,s.timezone, (select a.id from dose_actions a where a.occurrence_id=d.id order by a.occurrence_version desc limit 1) as last_action_id, (select a.recorded_at from dose_actions a where a.occurrence_id=d.id order by a.occurrence_version desc limit 1) as action_recorded_at from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id join medications m on m.id=s.medication_id where d.id=? and m.user_id=?",
            (rs, n) -> view(rs, now),
            id,
            owner)
        .stream()
        .findFirst();
  }

  private DoseDtos.View view(java.sql.ResultSet rs, Instant now) throws java.sql.SQLException {
    Instant due = rs.getTimestamp("due_at_utc").toInstant();
    String status = rs.getString("status");
    if (status.equals("PENDING") || status.equals("DUE"))
      status = due.isAfter(now) ? "PENDING" : "DUE";
    return new DoseDtos.View(
        rs.getObject("id", UUID.class),
        rs.getObject("medication_id", UUID.class),
        rs.getObject("schedule_version_id", UUID.class),
        rs.getInt("schedule_version_number"),
        rs.getString("medication_name"),
        rs.getString("unit"),
        rs.getBigDecimal("quantity_per_dose").stripTrailingZeros().toPlainString(),
        due,
        rs.getObject("local_date", LocalDate.class),
        rs.getObject("local_time", LocalTime.class).toString(),
        rs.getString("timezone"),
        status,
        rs.getLong("version"),
        rs.getObject("last_action_id", UUID.class),
        rs.getTimestamp("action_recorded_at") == null
            ? null
            : rs.getTimestamp("action_recorded_at").toInstant(),
        rs.getTimestamp("missed_at") == null ? null : rs.getTimestamp("missed_at").toInstant());
  }
}
