package com.medtrack.schedules;

import com.medtrack.account.CurrentUser;
import com.medtrack.auth.UserRepository;
import com.medtrack.common.ApiException;
import com.medtrack.doses.DoseStore;
import com.medtrack.medications.MedicationRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {
  private final MedicationRepository medications;
  private final ScheduleRepository versions;
  private final CurrentUser current;
  private final Clock clock;
  private final ScheduleLifecycle lifecycle;
  private final OccurrenceGenerator generator;
  private final DoseStore doses;
  private final UserRepository users;

  public ScheduleService(
      MedicationRepository medications,
      ScheduleRepository versions,
      CurrentUser current,
      Clock clock,
      ScheduleLifecycle lifecycle,
      OccurrenceGenerator generator,
      DoseStore doses,
      UserRepository users) {
    this.medications = medications;
    this.versions = versions;
    this.current = current;
    this.clock = clock;
    this.lifecycle = lifecycle;
    this.generator = generator;
    this.doses = doses;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public ScheduleDtos.History list(UUID medicationId, int page, int size) {
    medications
        .findByIdAndUserId(medicationId, current.id())
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
    var rows =
        versions.findByMedicationId(
            medicationId,
            org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("version").descending()));
    return new ScheduleDtos.History(
        rows.getContent().stream().map(ScheduleDtos.View::from).toList(),
        page,
        size,
        rows.getTotalElements(),
        rows.getTotalPages());
  }

  @Transactional
  public ScheduleDtos.View create(UUID medicationId, ScheduleDtos.Create input) {
    var medication =
        medications
            .findOwnedForUpdate(medicationId, current.id())
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
    if (medication.archivedAt() != null) throw conflict("Medication is archived");
    if (!ZoneId.getAvailableZoneIds().contains(input.timezone()))
      throw invalid("timezone", "Invalid IANA timezone");
    if (input.startDate().getYear() < 1
        || input.startDate().getYear() > 9998
        || input.endDate() != null
            && (input.endDate().getYear() > 9998 || input.endDate().isBefore(input.startDate())))
      throw invalid("endDate", "Invalid date range");
    var times = input.times().stream().map(LocalTime::parse).sorted().toList();
    if (new HashSet<>(times).size() != times.size())
      throw invalid("times", "Duplicate times are not allowed");
    ZoneId zone = ZoneId.of(input.timezone());
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var existing = versions.findByMedicationIdOrderByVersionAsc(medicationId);
    Instant boundary = input.startDate().atStartOfDay(zone).toInstant();
    if (!existing.isEmpty()) {
      if (input.startDate().isBefore(now.atZone(zone).toLocalDate()))
        throw invalid("startDate", "A revision cannot change past dates");
      if (!boundary.isAfter(now)) boundary = now.plusNanos(1000);
      if (doses.hasFutureFinalized(medicationId, boundary, now))
        throw conflict("Future confirmed records must be preserved");
      lifecycle.stopAt(medicationId, boundary, now);
    }
    int number = existing.isEmpty() ? 1 : existing.getLast().version() + 1;
    var version =
        new ScheduleVersion(
            medicationId,
            number,
            input.quantityPerDose(),
            input.timezone(),
            input.startDate(),
            input.endDate(),
            boundary,
            now,
            times);
    version = versions.saveAndFlush(version);
    ZoneId accountZone = ZoneId.of(users.findById(current.id()).orElseThrow().timezone());
    LocalDate today = now.atZone(accountZone).toLocalDate();
    generator.generate(
        medication,
        version,
        today.atStartOfDay(accountZone).toInstant(),
        today.plusDays(14).atStartOfDay(accountZone).toInstant());
    return ScheduleDtos.View.from(version);
  }

  private ApiException invalid(String field, String message) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_ERROR",
        "Request validation failed",
        Map.of(field, message));
  }

  private ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT", message);
  }
}
