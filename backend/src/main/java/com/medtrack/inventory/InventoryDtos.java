package com.medtrack.inventory;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class InventoryDtos {
  private InventoryDtos() {}

  public enum AdjustmentReason {
    RESTOCK,
    CORRECTION
  }

  public record Adjustment(
      @NotNull AdjustmentReason reason,
      @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal quantity,
      @NotNull UUID clientOperationId) {
    @JsonAnySetter
    public void unknown(String key, Object value) {
      throw new IllegalArgumentException("Unsupported adjustment field");
    }

    @Override
    public String toString() {
      return "InventoryAdjustment[REDACTED]";
    }
  }

  public record Balance(String quantity, boolean known, long version, List<String> warnings) {
    @Override
    public String toString() {
      return "InventoryBalance[REDACTED]";
    }
  }

  public record AdjustmentResult(
      UUID ledgerEntryId,
      UUID medicationId,
      UUID clientOperationId,
      String reason,
      String changeQuantity,
      Balance balance,
      Instant createdAt) {
    @Override
    public String toString() {
      return "AdjustmentResult[REDACTED]";
    }
  }

  public record Movement(
      UUID id,
      String changeQuantity,
      String unit,
      String reason,
      UUID doseActionId,
      Instant createdAt) {}

  public record History(
      List<Movement> content, int page, int size, long totalElements, int totalPages) {
    @Override
    public String toString() {
      return "InventoryHistory[REDACTED]";
    }
  }

  public record Projection(
      String status,
      String reason,
      String dailyConsumption,
      String fullDays,
      Instant estimatedDepletionAt,
      LocalDate estimatedDepletionDate,
      String timezone) {
    public static Projection unknown(String reason) {
      return new Projection("UNKNOWN", reason, null, null, null, null, null);
    }
  }

  public record View(
      UUID medicationId, String unit, Balance balance, Projection projection, History movements) {
    @Override
    public String toString() {
      return "InventoryView[REDACTED]";
    }
  }

  public static String decimal(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }
}
