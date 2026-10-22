package com.medtrack.schedules;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/medications/{medicationId}/schedules")
@Tag(name = "Schedules")
public class ScheduleController {
  private final ScheduleService schedules;

  public ScheduleController(ScheduleService schedules) {
    this.schedules = schedules;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a new schedule version",
      description =
          "Replaces future plans at the startDate boundary; today's revision starts now. Preserves past occurrences. DST gap shifts forward; overlap picks earlier offset.")
  public ScheduleDtos.View create(
      @PathVariable UUID medicationId, @Valid @RequestBody ScheduleDtos.Create input) {
    return schedules.create(medicationId, input);
  }
}
