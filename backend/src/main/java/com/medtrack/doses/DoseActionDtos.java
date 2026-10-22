package com.medtrack.doses;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.medtrack.inventory.InventoryDtos;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class DoseActionDtos {
  private DoseActionDtos() {}

  public enum Action {
    TAKEN,
    SKIPPED,
    UNDO
  }

  public record Request(
      @NotNull Action action,
      @NotNull UUID clientOperationId,
      @NotNull Instant recordedAt,
      UUID undoActionId,
      @PositiveOrZero Long expectedVersion) {
    @JsonAnySetter
    public void unknown(String key, Object value) {
      throw new IllegalArgumentException("Unsupported action field");
    }

    @Override
    public String toString() {
      return "DoseActionRequest[REDACTED]";
    }
  }

  public record Result(
      UUID actionId,
      UUID occurrenceId,
      UUID clientOperationId,
      String action,
      UUID undoActionId,
      String status,
      Instant recordedAt,
      Instant serverRecordedAt,
      long occurrenceVersion,
      String inventoryDelta,
      InventoryDtos.Balance balance) {
    @Override
    public String toString() {
      return "DoseActionResult[REDACTED]";
    }
  }

  public record History(
      List<Result> content, int page, int size, long totalElements, int totalPages) {
    @Override
    public String toString() {
      return "DoseActionHistory[REDACTED]";
    }
  }
}
