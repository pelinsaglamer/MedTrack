package com.medtrack.doses;

import com.medtrack.account.CurrentUser;
import com.medtrack.auth.UserRepository;
import com.medtrack.common.ApiException;
import com.medtrack.medications.MedicationRepository;
import com.medtrack.schedules.*;
import java.time.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoseService {
  private final MedicationRepository medications;
  private final ScheduleRepository schedules;
  private final OccurrenceGenerator generator;
  private final DoseStore doses;
  private final UserRepository users;
  private final CurrentUser current;
  private final Clock clock;
  private final DoseStateService states;

  public DoseService(
      MedicationRepository medications,
      ScheduleRepository schedules,
      OccurrenceGenerator generator,
      DoseStore doses,
      UserRepository users,
      CurrentUser current,
      Clock clock,
      DoseStateService states) {
    this.medications = medications;
    this.schedules = schedules;
    this.generator = generator;
    this.doses = doses;
    this.users = users;
    this.current = current;
    this.clock = clock;
    this.states = states;
  }

  @Transactional
  public DoseDtos.Page list(LocalDate requested, int page, int size) {
    var owner = current.id();
    ZoneId zone = ZoneId.of(users.findById(owner).orElseThrow().timezone());
    Instant now = clock.instant();
    LocalDate today = now.atZone(zone).toLocalDate();
    LocalDate date = requested == null ? today : requested;
    if (date.getYear() < 1 || date.isAfter(today.plusDays(13)))
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "Date exceeds supported horizon",
          Map.of("date", "Use a past date or the next 14 days"));
    // Lock in UUID order, same order for all concurrent daily reads.
    var medicationIds = medications.findOwnerIds(owner);
    for (var id : medicationIds) {
      var medication = medications.findOwnedForUpdate(id, owner).orElseThrow();
      for (var schedule : schedules.findByMedicationIdOrderByVersionAsc(id)) {
        generator.generate(
            medication,
            schedule,
            today.atStartOfDay(zone).toInstant(),
            today.plusDays(14).atStartOfDay(zone).toInstant());
        if (date.isBefore(today))
          generator.generate(
              medication,
              schedule,
              date.atStartOfDay(zone).toInstant(),
              date.plusDays(1).atStartOfDay(zone).toInstant());
      }
    }
    for (var id : medicationIds) states.refresh(id, now);
    return doses.list(owner, date, zone, page, size, now);
  }

  @Transactional
  public DoseDtos.WeeklySummary summary(LocalDate requested) {
    var owner = current.id();
    ZoneId zone = ZoneId.of(users.findById(owner).orElseThrow().timezone());
    Instant now = clock.instant();
    LocalDate today = now.atZone(zone).toLocalDate();
    LocalDate start = requested == null ? today.with(java.time.DayOfWeek.MONDAY) : requested;
    if (start.getYear() < 1
        || start.getDayOfWeek() != java.time.DayOfWeek.MONDAY
        || start.isAfter(today))
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "weekStart must be a current or past Monday");
    var ids = medications.findOwnerIds(owner);
    for (var id : ids) {
      var med = medications.findOwnedForUpdate(id, owner).orElseThrow();
      for (var schedule : schedules.findByMedicationIdOrderByVersionAsc(id)) {
        generator.generate(
            med,
            schedule,
            start.atStartOfDay(zone).toInstant(),
            start.plusDays(7).atStartOfDay(zone).toInstant());
      }
      states.refresh(id, now);
    }
    return doses.summary(owner, start, zone, now);
  }
}
