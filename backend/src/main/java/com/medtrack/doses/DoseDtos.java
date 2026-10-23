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
      String status,
      long version,
      UUID lastActionId,
      Instant recordedAt,
      Instant missedAt,
      Instant snoozedUntil) {
    @Override
    public String toString() {
      return "DoseView[REDACTED]";
    }
  }

  public record WeeklySummary(
      java.time.LocalDate weekStart,
      java.time.LocalDate weekEndExclusive,
      String timezone,
      long scheduledDoses,
      long taken,
      long skipped,
      long missed,
      long awaitingConfirmation,
      long pending,
      long unconfirmedAtDeadline) {
    @Override
    public String toString() {
      return "WeeklySummary[REDACTED]";
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
