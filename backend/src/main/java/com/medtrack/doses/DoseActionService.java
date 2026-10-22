package com.medtrack.doses;

import com.medtrack.account.CurrentUser;
import com.medtrack.common.*;
import com.medtrack.inventory.*;
import com.medtrack.medications.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoseActionService {
  private final DoseActionStore actions;
  private final MedicationRepository medications;
  private final CurrentUser current;
  private final InventoryStore inventory;
  private final OperationRegistry operations;
  private final Clock clock;
  private final DoseStateService states;

  public DoseActionService(
      DoseActionStore actions,
      MedicationRepository medications,
      CurrentUser current,
      InventoryStore inventory,
      OperationRegistry operations,
      Clock clock,
      DoseStateService states) {
    this.actions = actions;
    this.medications = medications;
    this.current = current;
    this.inventory = inventory;
    this.operations = operations;
    this.clock = clock;
    this.states = states;
  }

  private record Fingerprint(String type, UUID occurrenceId, DoseActionDtos.Request request) {}

  @Transactional
  public DoseActionDtos.Result act(
      UUID occurrenceId, UUID operation, DoseActionDtos.Request request) {
    operations.requireMatching(operation, request.clientOperationId());
    var occurrence = owned(occurrenceId);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var replay =
        operations.replay(
            operation,
            current.id(),
            occurrence.medicationId(),
            new Fingerprint("DOSE_ACTION", occurrenceId, request),
            now,
            DoseActionDtos.Result.class);
    if (replay != null) return replay;
    var med =
        medications
            .findOwnedForUpdate(occurrence.medicationId(), current.id())
            .orElseThrow(this::missing);
    states.refresh(occurrence.medicationId(), now);
    occurrence = owned(occurrenceId);
    if (request.recordedAt().isAfter(clock.instant()))
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "recordedAt cannot be in the future");
    if (request.expectedVersion() != null && request.expectedVersion() != occurrence.version())
      throw conflict();
    if (request.action() == DoseActionDtos.Action.UNDO)
      return undo(occurrence, med, operation, request, now);
    if (request.undoActionId() != null)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "undoActionId is only used for UNDO");
    if (occurrence.dueAt().isAfter(now)
        || occurrence.status().equals("TAKEN")
        || occurrence.status().equals("SKIPPED")) throw conflict();
    if (request.action() == DoseActionDtos.Action.TAKEN && !med.unit().equals(occurrence.unit()))
      throw new ApiException(
          HttpStatus.CONFLICT, "MEDICATION_UNIT_CONFLICT", "Dose and stock units do not match");
    String before = occurrence.status().equals("MISSED") ? "MISSED" : "DUE";
    return commit(
        occurrence,
        med,
        operation,
        request,
        now,
        before,
        request.action().name(),
        null,
        request.action() == DoseActionDtos.Action.TAKEN
            ? occurrence.quantity().negate()
            : BigDecimal.ZERO);
  }

  private DoseActionDtos.Result undo(
      DoseActionStore.Occurrence occurrence,
      Medication med,
      UUID operation,
      DoseActionDtos.Request request,
      Instant now) {
    if (request.undoActionId() == null)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "undoActionId is required for UNDO");
    var latest = actions.latest(occurrence.id());
    if (latest == null
        || !latest.id().equals(request.undoActionId())
        || latest.action().equals("UNDO")
        || !latest.action().equals(occurrence.status())) throw conflict();
    BigDecimal delta =
        latest.action().equals("TAKEN")
            ? inventory.consumptionDelta(latest.id()).negate()
            : BigDecimal.ZERO;
    String restored = states.unconfirmed(occurrence.medicationId(), occurrence.dueAt(), now);
    return commit(
        occurrence,
        med,
        operation,
        request,
        now,
        occurrence.status(),
        restored,
        latest.id(),
        delta);
  }

  private DoseActionDtos.Result commit(
      DoseActionStore.Occurrence occurrence,
      Medication med,
      UUID operation,
      DoseActionDtos.Request request,
      Instant now,
      String before,
      String after,
      UUID undoOf,
      BigDecimal delta) {
    UUID id = UUID.randomUUID();
    Instant recorded = request.recordedAt().truncatedTo(ChronoUnit.MICROS);
    actions.append(
        id, occurrence, operation, request.action().name(), undoOf, before, after, recorded, now);
    var snapshot = inventory.snapshot(med.id());
    var balance = inventory.balance(snapshot);
    if (delta.signum() != 0) {
      inventory.append(
          med,
          delta,
          request.action() == DoseActionDtos.Action.UNDO ? "UNDO" : "CONSUMPTION",
          id,
          operation,
          now);
      balance = inventory.apply(med.id(), snapshot, false, now);
    }
    if (!actions.transition(occurrence, after)) throw conflict();
    if (after.equals("MISSED"))
      actions.retainMissedAt(occurrence.id(), states.next(med.id(), occurrence.dueAt()));
    var result =
        new DoseActionDtos.Result(
            id,
            occurrence.id(),
            operation,
            request.action().name(),
            undoOf,
            after,
            recorded,
            now,
            occurrence.version() + 1,
            InventoryDtos.decimal(delta),
            balance);
    operations.complete(operation, result);
    return result;
  }

  @Transactional
  public DoseActionDtos.History history(UUID occurrenceId, int page, int size) {
    var occurrence = owned(occurrenceId);
    medications
        .findOwnedForUpdate(occurrence.medicationId(), current.id())
        .orElseThrow(this::missing);
    owned(occurrenceId);
    return actions.history(occurrenceId, page, size);
  }

  private DoseActionStore.Occurrence owned(UUID id) {
    var result = actions.owned(id, current.id());
    if (result == null) throw missing();
    return result;
  }

  private ApiException conflict() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "DOSE_STATE_CONFLICT",
        "Dose state changed or the action is not permitted");
  }

  private ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }
}
