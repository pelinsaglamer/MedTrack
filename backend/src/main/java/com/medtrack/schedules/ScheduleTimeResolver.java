package com.medtrack.schedules;

import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class ScheduleTimeResolver {
  // Gap: shift by the gap length. Overlap: pick the earlier offset, never generate twice.
  public Instant resolve(LocalDate date, LocalTime time, ZoneId zone) {
    return ZonedDateTime.ofLocal(LocalDateTime.of(date, time), zone, null)
        .withEarlierOffsetAtOverlap()
        .toInstant();
  }
}
