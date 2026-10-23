package com.medtrack.medications;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MedicationDtos {
  private MedicationDtos() {}

  private static void rejectUnknown() {
    throw new IllegalArgumentException("Unsupported medication field");
  }

  public record Create(
      @NotBlank @Size(max = 200) @Schema(example = "Example Medicine A") String name,
      @NotBlank @Size(max = 40) @Schema(example = "tablet") String unit,
      @DecimalMin("0")
          @Digits(integer = 13, fraction = 6)
          @Schema(
              type = "string",
              example = "30",
              description = "Opening amount, null means unknown; not current inventory")
          BigDecimal initialStock,
      @Size(max = 100)
          @Schema(
              example = "20 mg/tablet",
              description =
                  "Optional package strength label; never used for stock or dose conversion")
          String strength) {
    @JsonAnySetter
    public void unsupported(String key, Object value) {
      rejectUnknown();
    }

    @Override
    public String toString() {
      return "CreateMedication[REDACTED]";
    }
  }

  public record Patch(
      @Size(max = 200) String name,
      @Size(max = 40) String unit,
      Boolean archived,
      @Size(max = 100) String strength) {
    @JsonAnySetter
    public void unsupported(String key, Object value) {
      rejectUnknown();
    }

    @Override
    public String toString() {
      return "PatchMedication[REDACTED]";
    }
  }

  public record View(
      UUID id,
      String name,
      String unit,
      String strength,
      @Schema(
              type = "string",
              example = "30",
              description = "Immutable opening amount, not current inventory")
          String initialStock,
      Instant archivedAt,
      Instant createdAt) {
    public static View from(Medication medication) {
      return new View(
          medication.id(),
          medication.name(),
          medication.unit(),
          medication.strength(),
          medication.initialStock() == null
              ? null
              : medication.initialStock().stripTrailingZeros().toPlainString(),
          medication.archivedAt(),
          medication.createdAt());
    }

    @Override
    public String toString() {
      return "MedicationView[REDACTED]";
    }
  }

  public record Page(List<View> content, int page, int size, long totalElements, int totalPages) {
    @Override
    public String toString() {
      return "MedicationPage[REDACTED]";
    }
  }
}
