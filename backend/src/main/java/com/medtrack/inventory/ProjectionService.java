package com.medtrack.inventory;

import com.medtrack.medications.Medication;
import com.medtrack.schedules.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProjectionService {
  private final ScheduleRepository schedules;
  private final ScheduleTimeResolver resolver;
  private final JdbcTemplate jdbc;

  public ProjectionService(
      ScheduleRepository schedules, ScheduleTimeResolver resolver, JdbcTemplate jdbc) {
    this.schedules = schedules;
    this.resolver = resolver;
    this.jdbc = jdbc;
  }

  public InventoryDtos.Projection forecast(Medication med, BigDecimal balance, Instant now) {
    if (balance == null) return InventoryDtos.Projection.unknown("UNKNOWN_STOCK");
    if (med.archivedAt() != null) return InventoryDtos.Projection.unknown("ARCHIVED_MEDICATION");
    if (balance.signum() <= 0) return InventoryDtos.Projection.unknown("NON_POSITIVE_STOCK");
    var candidates =
        schedules.findByMedicationIdOrderByVersionAsc(med.id()).stream()
            .filter(s -> s.until() == null || s.until().isAfter(now) && s.until().isAfter(s.from()))
            .toList();
    if (candidates.isEmpty()) return InventoryDtos.Projection.unknown("NO_FUTURE_SCHEDULE");
    if (candidates.size() != 1) return InventoryDtos.Projection.unknown("VARIABLE_SCHEDULE");
    var s = candidates.getFirst();
    ZoneId zone = ZoneId.of(s.timezone());
    if (s.times().isEmpty()) return InventoryDtos.Projection.unknown("INVALID_SCHEDULE");
    if (s.from().isAfter(now.plus(Duration.ofDays(3650))))
      return InventoryDtos.Projection.unknown("FORECAST_HORIZON_LIMIT");
    Set<String> confirmed =
        new HashSet<>(
            jdbc.query(
                "select local_date,local_time from dose_occurrences where schedule_version_id=? and due_at_utc>? and status in ('TAKEN','SKIPPED')",
                (rs, n) ->
                    rs.getObject("local_date", LocalDate.class)
                        + "/"
                        + rs.getObject("local_time", LocalTime.class),
                s.id(),
                java.sql.Timestamp.from(now)));
    BigDecimal daily = s.quantity().multiply(BigDecimal.valueOf(s.times().size()));
    String days = balance.divide(daily, 0, RoundingMode.FLOOR).toPlainString();
    BigDecimal remaining = balance;
    LocalDate first = now.atZone(zone).toLocalDate();
    if (first.isBefore(s.startDate())) first = s.startDate();
    for (int i = 0; i < 3650; i++) {
      LocalDate date = first.plusDays(i);
      if (s.endDate() != null && date.isAfter(s.endDate()))
        return InventoryDtos.Projection.unknown("PLAN_ENDS_BEFORE_DEPLETION");
      for (var slot : s.times()) {
        Instant due = resolver.resolve(date, slot.time(), zone);
        if (!due.isAfter(now) || due.isBefore(s.from())) continue;
        if (s.until() != null && !due.isBefore(s.until()))
          return InventoryDtos.Projection.unknown("PLAN_ENDS_BEFORE_DEPLETION");
        if (confirmed.contains(date + "/" + slot.time())) continue;
        if (remaining.compareTo(s.quantity()) <= 0)
          return new InventoryDtos.Projection(
              "ESTIMATED",
              null,
              InventoryDtos.decimal(daily),
              days,
              due,
              due.atZone(zone).toLocalDate(),
              zone.getId());
        remaining = remaining.subtract(s.quantity());
      }
    }
    return InventoryDtos.Projection.unknown("FORECAST_HORIZON_LIMIT");
  }
}
