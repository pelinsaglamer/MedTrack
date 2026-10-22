package com.medtrack.schedules;

import com.medtrack.doses.DoseStore;
import com.medtrack.medications.Medication;
import java.time.*;
import org.springframework.stereotype.Service;

@Service
public class OccurrenceGenerator {
  private final DoseStore doses;
  private final ScheduleTimeResolver resolver;

  public OccurrenceGenerator(DoseStore doses, ScheduleTimeResolver resolver) {
    this.doses = doses;
    this.resolver = resolver;
  }

  // Caller holds the medication row lock, shared by generation, schedule edit and archive.
  public void generate(
      Medication medication, ScheduleVersion version, Instant windowFrom, Instant windowUntil) {
    ZoneId zone = ZoneId.of(version.timezone());
    LocalDate first = windowFrom.atZone(zone).toLocalDate().minusDays(1);
    LocalDate last = windowUntil.atZone(zone).toLocalDate().plusDays(1);
    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
      if (date.isBefore(version.startDate())
          || version.endDate() != null && date.isAfter(version.endDate())) continue;
      for (var slot : version.times()) {
        Instant due = resolver.resolve(date, slot.time(), zone);
        if (due.isBefore(windowFrom)
            || !due.isBefore(windowUntil)
            || due.isBefore(version.from())
            || version.until() != null && !due.isBefore(version.until())
            || medication.archivedAt() != null && due.isAfter(medication.archivedAt())) continue;
        doses.insert(version, slot, medication, date, due);
      }
    }
  }
}
