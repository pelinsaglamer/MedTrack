package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties =
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5433/medtrack_test}")
@ActiveProfiles("test")
class InventoryMigrationTests {
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate admin;

  @Value("${spring.datasource.username}")
  String username;

  @Value("${spring.datasource.password}")
  String password;

  @Test
  void phaseFourDataUpgradesOnceWithoutInventingUnknownOpeningStock() throws Exception {
    String database = "medtrack_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    String original;
    try (var connection = dataSource.getConnection()) {
      original = connection.getMetaData().getURL();
    }
    String url = original.substring(0, original.lastIndexOf('/') + 1) + database;
    admin.execute("create database " + database);
    try {
      var source = new DriverManagerDataSource(url, username, password);
      var jdbc = new JdbcTemplate(source);
      Flyway.configure().dataSource(source).target("4").load().migrate();
      UUID user = UUID.randomUUID(), known = UUID.randomUUID(), unknown = UUID.randomUUID();
      jdbc.update(
          "insert into users(id,email,password_hash,timezone) values (?,'migration-fixture@example.invalid','fixture-hash','Europe/Istanbul')",
          user);
      jdbc.update(
          "insert into medications(id,user_id,name,unit,initial_stock,created_at) values (?,?,'Fictional Known','tablet',30,now()), (?,?,'Fictional Unknown','ml',null,now())",
          known,
          user,
          unknown,
          user);
      var migration = Flyway.configure().dataSource(source).load();
      migration.migrate();
      migration.validate();
      assertNull(
          jdbc.queryForObject("select strength from medications where id=?", String.class, known));
      assertNull(
          jdbc.queryForObject(
              "select strength from medications where id=?", String.class, unknown));
      assertEquals(
          0,
          new BigDecimal("30")
              .compareTo(
                  jdbc.queryForObject(
                      "select quantity from inventory_snapshot where medication_id=?",
                      BigDecimal.class,
                      known)));
      assertNull(
          jdbc.queryForObject(
              "select quantity from inventory_snapshot where medication_id=?",
              BigDecimal.class,
              unknown));
      assertEquals(
          1,
          jdbc.queryForObject(
              "select count(*) from inventory_ledger where medication_id=? and reason='OPENING'",
              Integer.class,
              known));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from inventory_ledger where medication_id=?",
              Integer.class,
              unknown));
      assertEquals(0, migration.migrate().migrationsExecuted);
      assertEquals(
          1,
          jdbc.queryForObject(
              "select count(*) from inventory_ledger where medication_id=?", Integer.class, known));
    } finally {
      admin.execute("drop database " + database + " with (force)");
    }
  }
}
