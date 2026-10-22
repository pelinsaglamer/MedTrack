package com.medtrack.doses;

import com.medtrack.schedules.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DoseStateService {
  private final ScheduleRepository schedules;
  private final ScheduleTimeResolver resolver;
  private final JdbcTemplate jdbc;

  public DoseStateService(
      ScheduleRepository schedules, ScheduleTimeResolver resolver, JdbcTemplate jdbc) {
    this.schedules = schedules;
    this.resolver = resolver;
    this.jdbc = jdbc;
  }

  // Caller holds the medication lock. A derived MISSED transition never changes stock.
  public void refresh(UUID medicationId, Instant now) {
    var versions = schedules.findByMedicationIdOrderByVersionAsc(medicationId);
    var rows =
        jdbc.query(
            "select d.id,d.due_at_utc from dose_occurrences d join schedule_versions s on s.id=d.schedule_version_id where s.medication_id=? and d.status in ('PENDING','DUE') and d.due_at_utc<=?",
            (rs, n) ->
                Map.entry(
                    rs.getObject("id", UUID.class), rs.getTimestamp("due_at_utc").toInstant()),
            medicationId,
            Timestamp.from(now));
    for (var row : rows) {
      Instant deadline = next(versions, row.getValue());
      if (deadline != null && !deadline.isAfter(now))
        jdbc.update(
            "update dose_occurrences set status='MISSED',missed_at=coalesce(missed_at,?),version=version+1 where id=? and status in ('PENDING','DUE')",
            Timestamp.from(deadline),
            row.getKey());
    }
  }

  public Instant next(UUID medicationId, Instant due) {
    return next(schedules.findByMedicationIdOrderByVersionAsc(medicationId), due);
  }

  private Instant next(List<ScheduleVersion> versions, Instant reference) {
    Instant result = null;
    for (var version : versions) {
      if (version.until() != null
          && (!version.until().isAfter(reference) || !version.until().isAfter(version.from())))
        continue;
      ZoneId zone = ZoneId.of(version.timezone());
      LocalDate start = reference.atZone(zone).toLocalDate();
      if (start.isBefore(version.startDate())) start = version.startDate();
      if (start.isBefore(version.from().atZone(zone).toLocalDate()))
        start = version.from().atZone(zone).toLocalDate();
      for (int day = 0; day < 3; day++) {
        LocalDate date = start.plusDays(day);
        if (version.endDate() != null && date.isAfter(version.endDate())) break;
        for (var slot : version.times()) {
          Instant candidate = resolver.resolve(date, slot.time(), zone);
          if (!candidate.isAfter(reference)
              || candidate.isBefore(version.from())
              || version.until() != null && !candidate.isBefore(version.until())) continue;
          if (result == null || candidate.isBefore(result)) result = candidate;
        }
      }
    }
    return result;
  }

  public String unconfirmed(UUID medicationId, Instant due, Instant now) {
    if (due.isAfter(now)) return "PENDING";
    Instant deadline = next(medicationId, due);
    return deadline != null && !deadline.isAfter(now) ? "MISSED" : "DUE";
  }
}
