package com.medtrack.inventory;

import com.medtrack.common.ApiException;
import com.medtrack.medications.Medication;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryStore {
  private final JdbcTemplate jdbc;

  public InventoryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Snapshot(BigDecimal quantity, long version) {}

  // Invoked during medication creation, in its transaction, after JPA flush.
  public void initialize(Medication medication) {
    jdbc.update(
        "insert into inventory_snapshot(medication_id,quantity,updated_at) values (?,?,?)",
        medication.id(),
        medication.initialStock(),
        Timestamp.from(medication.createdAt()));
    if (medication.initialStock() != null)
      append(medication, medication.initialStock(), "OPENING", null, null, medication.createdAt());
  }

  public Snapshot snapshot(UUID med) {
    return jdbc.queryForObject(
        "select quantity,version from inventory_snapshot where medication_id=?",
        (rs, n) -> new Snapshot(rs.getBigDecimal("quantity"), rs.getLong("version")),
        med);
  }

  public BigDecimal sum(UUID med) {
    return jdbc.queryForObject(
        "select coalesce(sum(change_quantity),0) from inventory_ledger where medication_id=?",
        BigDecimal.class,
        med);
  }

  public UUID append(
      Medication med, BigDecimal delta, String reason, UUID action, UUID operation, Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into inventory_ledger(id,medication_id,change_quantity,unit,reason,dose_action_id,operation_id,created_at) values (?,?,?,?,?,?,?,?)",
        id,
        med.id(),
        delta,
        med.unit(),
        reason,
        action,
        operation,
        Timestamp.from(now));
    return id;
  }

  public InventoryDtos.Balance apply(
      UUID med, Snapshot previous, boolean establishesKnown, Instant now) {
    BigDecimal quantity = previous.quantity() != null || establishesKnown ? sum(med) : null;
    int count =
        jdbc.update(
            "update inventory_snapshot set quantity=?,updated_at=?,version=version+1 where medication_id=? and version=?",
            quantity,
            Timestamp.from(now),
            med,
            previous.version());
    if (count != 1)
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVENTORY_VERSION_CONFLICT",
          "Inventory changed; retry with current state");
    return balance(new Snapshot(quantity, previous.version() + 1));
  }

  public InventoryDtos.Balance balance(Snapshot snapshot) {
    return new InventoryDtos.Balance(
        InventoryDtos.decimal(snapshot.quantity()),
        snapshot.quantity() != null,
        snapshot.version(),
        snapshot.quantity() == null
            ? List.of("STOCK_UNKNOWN")
            : snapshot.quantity().signum() < 0 ? List.of("STOCK_NEGATIVE") : List.of());
  }

  public boolean hasMovements(UUID med) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from inventory_ledger where medication_id=?)",
            Boolean.class,
            med));
  }

  public InventoryDtos.History history(UUID med, int page, int size) {
    long count =
        jdbc.queryForObject(
            "select count(*) from inventory_ledger where medication_id=?", Long.class, med);
    var rows =
        jdbc.query(
            "select * from inventory_ledger where medication_id=? order by created_at desc,id desc limit ? offset ?",
            (rs, n) ->
                new InventoryDtos.Movement(
                    rs.getObject("id", UUID.class),
                    InventoryDtos.decimal(rs.getBigDecimal("change_quantity")),
                    rs.getString("unit"),
                    rs.getString("reason"),
                    rs.getObject("dose_action_id", UUID.class),
                    rs.getTimestamp("created_at").toInstant()),
            med,
            size,
            (long) page * size);
    return new InventoryDtos.History(rows, page, size, count, (int) ((count + size - 1) / size));
  }

  public BigDecimal consumptionDelta(UUID action) {
    return jdbc.queryForObject(
        "select change_quantity from inventory_ledger where dose_action_id=? and reason='CONSUMPTION'",
        BigDecimal.class,
        action);
  }
}
