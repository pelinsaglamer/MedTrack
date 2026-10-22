package com.medtrack.schedules;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Entity
@Table(name = "schedule_versions")
public class ScheduleVersion {
  @Id private UUID id;

  @Column(name = "medication_id", nullable = false)
  private UUID medicationId;

  @Column(nullable = false)
  private int version;

  @Column(name = "quantity_per_dose", nullable = false, precision = 19, scale = 6)
  private BigDecimal quantityPerDose;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_until")
  private Instant effectiveUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "scheduleVersion", cascade = CascadeType.ALL)
  @OrderBy("ordinal ASC")
  private List<ScheduleTime> times = new ArrayList<>();

  protected ScheduleVersion() {}

  public ScheduleVersion(
      UUID medicationId,
      int version,
      BigDecimal quantity,
      String timezone,
      LocalDate start,
      LocalDate end,
      Instant from,
      Instant now,
      List<LocalTime> times) {
    id = UUID.randomUUID();
    this.medicationId = medicationId;
    this.version = version;
    quantityPerDose = quantity;
    this.timezone = timezone;
    startDate = start;
    endDate = end;
    effectiveFrom = from;
    createdAt = now.truncatedTo(ChronoUnit.MICROS);
    effectiveUntil =
        end == null ? null : end.plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant();
    for (int i = 0; i < times.size(); i++) this.times.add(new ScheduleTime(this, times.get(i), i));
  }

  public UUID id() {
    return id;
  }

  public UUID medicationId() {
    return medicationId;
  }

  public int version() {
    return version;
  }

  public BigDecimal quantity() {
    return quantityPerDose;
  }

  public String timezone() {
    return timezone;
  }

  public LocalDate startDate() {
    return startDate;
  }

  public LocalDate endDate() {
    return endDate;
  }

  public Instant from() {
    return effectiveFrom;
  }

  public Instant until() {
    return effectiveUntil;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public List<ScheduleTime> times() {
    return Collections.unmodifiableList(times);
  }

  public void stopAt(Instant boundary) {
    if (effectiveUntil == null || effectiveUntil.isAfter(boundary))
      effectiveUntil = effectiveFrom.isAfter(boundary) ? effectiveFrom : boundary;
  }
}
