package com.medtrack.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/{medicationId}")
@Tag(name = "Inventory")
public class InventoryController {
  private final InventoryService inventory;

  public InventoryController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @GetMapping
  @Operation(
      summary = "Read own stock, movement history and estimated depletion",
      description =
          "Unknown/variable stock or plans return an explicit UNKNOWN projection; never dosage advice")
  public InventoryDtos.View get(
      @PathVariable UUID medicationId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return inventory.get(medicationId, page, size);
  }

  @PostMapping("/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Restock or correct own stock",
      description =
          "RESTOCK quantity is a positive addition. CORRECTION quantity is the observed absolute nonnegative balance. Idempotency-Key must match clientOperationId.")
  public InventoryDtos.AdjustmentResult adjust(
      @PathVariable UUID medicationId,
      @RequestHeader("Idempotency-Key") UUID operation,
      @Valid @RequestBody InventoryDtos.Adjustment input) {
    return inventory.adjust(medicationId, operation, input);
  }
}
