package com.medtrack.doses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doses/{occurrenceId}/actions")
@Tag(name = "Dose actions")
public class DoseActionController {
  private final DoseActionService actions;

  public DoseActionController(DoseActionService actions) {
    this.actions = actions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Record an explicit dose action",
      description =
          "Idempotency-Key must equal clientOperationId. Exact replay returns the original event and balance without another stock change.")
  public DoseActionDtos.Result act(
      @PathVariable UUID occurrenceId,
      @RequestHeader("Idempotency-Key") UUID operation,
      @Valid @RequestBody DoseActionDtos.Request input) {
    return actions.act(occurrenceId, operation, input);
  }

  @GetMapping
  @Operation(summary = "Read own immutable action history")
  public DoseActionDtos.History history(
      @PathVariable UUID occurrenceId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return actions.history(occurrenceId, page, size);
  }
}
