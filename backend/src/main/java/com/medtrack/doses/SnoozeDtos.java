package com.medtrack.doses;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class SnoozeDtos {
  private SnoozeDtos() {}

  public record Request(
      @NotNull UUID clientOperationId,
      @NotNull Instant snoozedUntil,
      @PositiveOrZero Long expectedVersion) {
    @JsonAnySetter
    public void unknown(String key, Object value) {
      throw new IllegalArgumentException("Unsupported snooze field");
    }

    @Override
    public String toString() {
      return "SnoozeRequest[REDACTED]";
    }
  }

  public record Result(
      UUID occurrenceId, UUID clientOperationId, Instant snoozedUntil, long occurrenceVersion) {}
}
