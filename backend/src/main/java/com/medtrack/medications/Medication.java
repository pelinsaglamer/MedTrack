package com.medtrack.medications;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "medications")
public class Medication {
  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 40)
  private String unit;

  @Column(length = 100)
  private String strength;

  @Column(name = "initial_stock", precision = 19, scale = 6, updatable = false)
  private BigDecimal initialStock;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Medication() {}

  public Medication(
      UUID id, UUID userId, String name, String unit, BigDecimal initialStock, Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.unit = unit;
    this.initialStock = initialStock;
    this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String unit() {
    return unit;
  }

  public String strength() {
    return strength;
  }

  public void changeStrength(String strength) {
    this.strength = strength;
  }

  public BigDecimal initialStock() {
    return initialStock;
  }

  public Instant archivedAt() {
    return archivedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void changeUnit(String unit) {
    this.unit = unit;
  }

  public void archive(Instant now) {
    if (archivedAt == null) archivedAt = now.truncatedTo(ChronoUnit.MICROS);
  }
}
