package com.medtrack.schedules;

import com.medtrack.doses.DoseStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ScheduleLifecycle {
  private final ScheduleRepository versions;
  private final DoseStore doses;

  public ScheduleLifecycle(ScheduleRepository versions, DoseStore doses) {
    this.versions = versions;
    this.doses = doses;
  }

  // Runs inside the caller's transaction with the owner-scoped medication row locked.
  public void stopAt(UUID medicationId, Instant boundary, Instant now) {
    for (var version : versions.findByMedicationIdOrderByVersionAsc(medicationId))
      version.stopAt(boundary);
    versions.flush();
    doses.deleteFuturePending(medicationId, boundary, now);
  }
}
