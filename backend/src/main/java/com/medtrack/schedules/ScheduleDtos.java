package com.medtrack.schedules;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ScheduleDtos {
  private ScheduleDtos() {}

  public record Create(
      @NotEmpty @Size(max = 1440)
          List<@NotNull @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String> times,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @Digits(integer = 13, fraction = 6)
          @Schema(type = "string", example = "1")
          BigDecimal quantityPerDose,
      @NotBlank @Size(max = 64) String timezone,
      @NotNull LocalDate startDate,
      LocalDate endDate) {
    @JsonAnySetter
    public void unknown(String key, Object value) {
      throw new IllegalArgumentException("Unsupported schedule field");
    }

    @Override
    public String toString() {
      return "ScheduleCreate[REDACTED]";
    }
  }

  public record View(
      UUID id,
      UUID medicationId,
      int version,
      List<String> times,
      String quantityPerDose,
      String timezone,
      LocalDate startDate,
      LocalDate endDate,
      Instant effectiveFrom,
      Instant effectiveUntil,
      Instant createdAt) {
    public static View from(ScheduleVersion s) {
      return new View(
          s.id(),
          s.medicationId(),
          s.version(),
          s.times().stream().map(t -> t.time().toString()).toList(),
          s.quantity().stripTrailingZeros().toPlainString(),
          s.timezone(),
          s.startDate(),
          s.endDate(),
          s.from(),
          s.until(),
          s.createdAt());
    }

    @Override
    public String toString() {
      return "ScheduleView[REDACTED]";
    }
  }
}
