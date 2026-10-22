package com.medtrack.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_snapshot")
public class InventorySnapshot {
  @Id
  @Column(name = "medication_id")
  private UUID medicationId;

  @Column(precision = 30, scale = 6)
  private BigDecimal quantity;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected InventorySnapshot() {}
}
