package com.medtrack.schedules;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ScheduleRepository extends JpaRepository<ScheduleVersion, UUID> {
  @EntityGraph(attributePaths = "times")
  List<ScheduleVersion> findByMedicationIdOrderByVersionAsc(UUID medicationId);
}
