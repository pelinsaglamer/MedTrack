package com.medtrack.doses;

import com.medtrack.account.CurrentUser;
import com.medtrack.common.*;
import com.medtrack.medications.MedicationRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnoozeService {
  private final DoseActionStore actions;
  private final DoseStore doses;
  private final MedicationRepository medications;
  private final CurrentUser current;
  private final OperationRegistry operations;
  private final Clock clock;
  private final DoseStateService states;

  public SnoozeService(
      DoseActionStore actions,
      DoseStore doses,
      MedicationRepository medications,
      CurrentUser current,
      OperationRegistry operations,
      Clock clock,
      DoseStateService states) {
    this.actions = actions;
    this.doses = doses;
    this.medications = medications;
    this.current = current;
    this.operations = operations;
    this.clock = clock;
    this.states = states;
  }

  private record Fingerprint(String type, UUID occurrenceId, SnoozeDtos.Request request) {}

  @Transactional
  public SnoozeDtos.Result snooze(UUID id, UUID key, SnoozeDtos.Request input) {
    operations.requireMatching(key, input.clientOperationId());
    var dose = actions.owned(id, current.id());
    if (dose == null) throw missing();
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var previous =
        operations.replay(
            key,
            current.id(),
            dose.medicationId(),
            new Fingerprint("SNOOZE", id, input),
            now,
            SnoozeDtos.Result.class);
    if (previous != null) return previous;
    var med =
        medications
            .findOwnedForUpdate(dose.medicationId(), current.id())
            .orElseThrow(this::missing);
    states.refresh(med.id(), now);
    dose = actions.owned(id, current.id());
    if (dose == null) throw missing();
    if (med.archivedAt() != null
        || dose.dueAt().isAfter(now)
        || dose.status().equals("TAKEN")
        || dose.status().equals("SKIPPED")
        || input.expectedVersion() != null && input.expectedVersion() != dose.version())
      throw new ApiException(
          HttpStatus.CONFLICT, "DOSE_STATE_CONFLICT", "Dose is not eligible for snooze");
    var until = input.snoozedUntil().truncatedTo(ChronoUnit.MICROS);
    if (!until.isAfter(now) || until.atZone(ZoneOffset.UTC).getYear() > 9998)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "snoozedUntil must be in the future");
    if (!doses.snooze(id, dose.version(), until))
      throw new ApiException(HttpStatus.CONFLICT, "DOSE_STATE_CONFLICT", "Dose changed");
    var result = new SnoozeDtos.Result(id, key, until, dose.version() + 1);
    operations.complete(key, result);
    return result;
  }

  private ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }
}
