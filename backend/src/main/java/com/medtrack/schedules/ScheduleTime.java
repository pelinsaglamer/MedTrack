package com.medtrack.schedules;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_times")
public class ScheduleTime {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "schedule_version_id", nullable = false)
  private ScheduleVersion scheduleVersion;

  @Column(name = "local_time", nullable = false)
  private LocalTime localTime;

  @Column(nullable = false)
  private int ordinal;

  protected ScheduleTime() {}

  ScheduleTime(ScheduleVersion version, LocalTime time, int ordinal) {
    id = UUID.randomUUID();
    scheduleVersion = version;
    localTime = time;
    this.ordinal = ordinal;
  }

  public UUID id() {
    return id;
  }

  public LocalTime time() {
    return localTime;
  }
}
