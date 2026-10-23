package com.medtrack.doses;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doses/{id}/snoozes")
public class SnoozeController {
  private final SnoozeService service;

  public SnoozeController(SnoozeService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SnoozeDtos.Result snooze(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody SnoozeDtos.Request request) {
    return service.snooze(id, key, request);
  }
}
