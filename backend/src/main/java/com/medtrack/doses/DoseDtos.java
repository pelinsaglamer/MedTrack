package com.medtrack.doses;

import java.time.*;
import java.util.*;

public final class DoseDtos {
  private DoseDtos() {}

  public record View(
      UUID id,
      UUID medicationId,
      UUID scheduleVersionId,
      int scheduleVersion,
      String medicationName,
      String unit,
      String quantityPerDose,
      Instant dueAtUtc,
      LocalDate localDate,
      String localTime,
      String timezone,
      String status) {
    @Override
    public String toString() {
      return "DoseView[REDACTED]";
    }
  }

  public record Page(
      List<View> content,
      LocalDate date,
      String timezone,
      int page,
      int size,
      long totalElements,
      int totalPages) {
    @Override
    public String toString() {
      return "DosePage[REDACTED]";
    }
  }
}
