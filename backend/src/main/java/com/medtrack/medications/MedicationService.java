package com.medtrack.medications;

import com.medtrack.account.CurrentUser;
import com.medtrack.common.ApiException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicationService {
  private final MedicationRepository medications;
  private final CurrentUser current;
  private final Clock clock;
  private final com.medtrack.schedules.ScheduleLifecycle schedules;

  public MedicationService(
      MedicationRepository medications,
      CurrentUser current,
      Clock clock,
      com.medtrack.schedules.ScheduleLifecycle schedules) {
    this.medications = medications;
    this.current = current;
    this.clock = clock;
    this.schedules = schedules;
  }

  @Transactional
  public MedicationDtos.View create(MedicationDtos.Create input) {
    var medication =
        new Medication(
            UUID.randomUUID(),
            current.id(),
            clean(input.name(), "name"),
            clean(input.unit(), "unit"),
            input.initialStock(),
            clock.instant());
    return MedicationDtos.View.from(medications.saveAndFlush(medication));
  }

  @Transactional(readOnly = true)
  public MedicationDtos.View get(UUID id) {
    return MedicationDtos.View.from(
        medications.findByIdAndUserId(id, current.id()).orElseThrow(this::notFound));
  }

  @Transactional(readOnly = true)
  public MedicationDtos.Page list(int page, int size, boolean includeArchived) {
    var pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    var result =
        includeArchived
            ? medications.findByUserId(current.id(), pageable)
            : medications.findByUserIdAndArchivedAtIsNull(current.id(), pageable);
    return new MedicationDtos.Page(
        result.getContent().stream().map(MedicationDtos.View::from).toList(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional
  public MedicationDtos.View patch(UUID id, MedicationDtos.Patch input) {
    var medication = medications.findOwnedForUpdate(id, current.id()).orElseThrow(this::notFound);
    if (input.name() == null && input.unit() == null && input.archived() == null)
      throw invalid("request", "At least one field is required");
    if (Boolean.FALSE.equals(input.archived()))
      throw invalid("archived", "Only archiving is supported");
    if (input.name() != null) medication.rename(clean(input.name(), "name"));
    if (input.unit() != null) medication.changeUnit(clean(input.unit(), "unit"));
    if (Boolean.TRUE.equals(input.archived())) {
      medication.archive(clock.instant());
      schedules.stopAt(id, medication.archivedAt(), medication.archivedAt());
    }
    return MedicationDtos.View.from(medications.saveAndFlush(medication));
  }

  private String clean(String value, String field) {
    if (value == null || value.isBlank()) throw invalid(field, "Must not be blank");
    return value.strip();
  }

  private ApiException invalid(String field, String message) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_ERROR",
        "Request validation failed",
        Map.of(field, message));
  }

  private ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }
}
