package com.medtrack.doses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doses")
@Tag(name = "Doses")
public class DoseController {
  private final DoseService doses;

  public DoseController(DoseService doses) {
    this.doses = doses;
  }

  @GetMapping
  @Operation(
      summary = "List own daily doses",
      description =
          "Day boundaries use account timezone. Generates a rolling 14-day horizon. History is retained. PENDING/DUE are time-derived; never implies confirmed intake.")
  public DoseDtos.Page list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return doses.list(date, page, size);
  }

  @GetMapping("/summary")
  @Operation(
      summary = "Weekly counts of confirmed, skipped and unconfirmed doses",
      description =
          "Calendar week starts Monday in the account timezone. MISSED means no confirmation by the next planned dose, not proof of non-intake.")
  public DoseDtos.WeeklySummary summary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    return doses.summary(weekStart);
  }
}
