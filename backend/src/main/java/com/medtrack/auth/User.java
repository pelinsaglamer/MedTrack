package com.medtrack.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
  @Id private UUID id;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected User() {}

  public User(UUID id, String email, String passwordHash, String timezone, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.timezone = timezone;
    this.createdAt = createdAt;
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String timezone() {
    return timezone;
  }
}
