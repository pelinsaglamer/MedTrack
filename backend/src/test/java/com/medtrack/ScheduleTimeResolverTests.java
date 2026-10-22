package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.schedules.ScheduleTimeResolver;
import java.time.*;
import org.junit.jupiter.api.Test;

class ScheduleTimeResolverTests {
  final ScheduleTimeResolver resolver = new ScheduleTimeResolver();

  private Instant at(String date, String time, String zone) {
    return resolver.resolve(LocalDate.parse(date), LocalTime.parse(time), ZoneId.of(zone));
  }

  @Test
  void istanbulKeepsIntendedDailyTime() {
    assertEquals(
        Instant.parse("2026-10-22T06:00:00Z"), at("2026-10-22", "09:00", "Europe/Istanbul"));
  }

  @Test
  void berlinSpringGapShiftsByOneHour() {
    assertEquals(Instant.parse("2026-03-29T01:30:00Z"), at("2026-03-29", "02:30", "Europe/Berlin"));
  }

  @Test
  void berlinAutumnOverlapPicksFirstInstant() {
    assertEquals(Instant.parse("2026-10-25T00:30:00Z"), at("2026-10-25", "02:30", "Europe/Berlin"));
  }

  @Test
  void newYorkTransitionsUseTheSamePolicy() {
    assertEquals(
        Instant.parse("2026-03-08T07:30:00Z"), at("2026-03-08", "02:30", "America/New_York"));
    assertEquals(
        Instant.parse("2026-11-01T05:30:00Z"), at("2026-11-01", "01:30", "America/New_York"));
  }

  @Test
  void lordHoweHalfHourGapDoesNotAssumeSixtyMinutes() {
    assertEquals(
        Instant.parse("2026-10-03T15:45:00Z"), at("2026-10-04", "02:15", "Australia/Lord_Howe"));
  }

  @Test
  void dailyLocalTimesAreNotAlwaysTwentyFourHoursApart() {
    var before = at("2026-03-28", "09:00", "Europe/Berlin");
    var after = at("2026-03-29", "09:00", "Europe/Berlin");
    assertEquals(Duration.ofHours(23), Duration.between(before, after));
  }
}
