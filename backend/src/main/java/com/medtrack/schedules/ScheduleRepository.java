package com.medtrack.schedules;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ScheduleRepository extends JpaRepository<ScheduleVersion, UUID> {
  org.springframework.data.domain.Page<ScheduleVersion> findByMedicationId(
      UUID medicationId, org.springframework.data.domain.Pageable pageable);

  @EntityGraph(attributePaths = "times")
  List<ScheduleVersion> findByMedicationIdOrderByVersionAsc(UUID medicationId);
}
