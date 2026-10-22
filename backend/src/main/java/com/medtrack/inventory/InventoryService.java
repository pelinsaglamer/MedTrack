package com.medtrack.inventory;

import com.medtrack.account.CurrentUser;
import com.medtrack.common.*;
import com.medtrack.medications.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  private final MedicationRepository medications;
  private final CurrentUser current;
  private final InventoryStore inventory;
  private final OperationRegistry operations;
  private final ProjectionService projections;
  private final Clock clock;

  public InventoryService(
      MedicationRepository medications,
      CurrentUser current,
      InventoryStore inventory,
      OperationRegistry operations,
      ProjectionService projections,
      Clock clock) {
    this.medications = medications;
    this.current = current;
    this.inventory = inventory;
    this.operations = operations;
    this.projections = projections;
    this.clock = clock;
  }

  private record AdjustmentFingerprint(
      String type, UUID medicationId, String reason, String quantity) {}

  @Transactional
  public InventoryDtos.AdjustmentResult adjust(
      UUID med, UUID header, InventoryDtos.Adjustment input) {
    operations.requireMatching(header, input.clientOperationId());
    // Resolve ownership before looking up or returning any operation response.
    medications.findByIdAndUserId(med, current.id()).orElseThrow(this::missing);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var fingerprint =
        new AdjustmentFingerprint(
            "INVENTORY", med, input.reason().name(), InventoryDtos.decimal(input.quantity()));
    var replay =
        operations.replay(
            header, current.id(), med, fingerprint, now, InventoryDtos.AdjustmentResult.class);
    if (replay != null) return replay;
    var medication = medications.findOwnedForUpdate(med, current.id()).orElseThrow(this::missing);
    if (input.reason() == InventoryDtos.AdjustmentReason.RESTOCK && input.quantity().signum() <= 0)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Restock quantity must be positive");
    var previous = inventory.snapshot(med);
    boolean correction = input.reason() == InventoryDtos.AdjustmentReason.CORRECTION;
    BigDecimal delta =
        correction ? input.quantity().subtract(inventory.sum(med)) : input.quantity();
    UUID entry = inventory.append(medication, delta, input.reason().name(), null, header, now);
    var balance = inventory.apply(med, previous, correction, now);
    var result =
        new InventoryDtos.AdjustmentResult(
            entry, med, header, input.reason().name(), InventoryDtos.decimal(delta), balance, now);
    operations.complete(header, result);
    return result;
  }

  @Transactional
  public InventoryDtos.View get(UUID med, int page, int size) {
    var medication = medications.findOwnedForUpdate(med, current.id()).orElseThrow(this::missing);
    var snapshot = inventory.snapshot(med);
    return new InventoryDtos.View(
        med,
        medication.unit(),
        inventory.balance(snapshot),
        projections.forecast(medication, snapshot.quantity(), clock.instant()),
        inventory.history(med, page, size));
  }

  private ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }
}
