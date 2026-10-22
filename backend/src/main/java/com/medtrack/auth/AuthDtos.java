package com.medtrack.auth;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  public record Register(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 64) String timezone) {
    @Override
    public String toString() {
      return "Register[REDACTED]";
    }
  }

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
      return "Login[REDACTED]";
    }
  }

  public record Refresh(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {
    @Override
    public String toString() {
      return "Refresh[REDACTED]";
    }
  }

  public record Account(UUID id, String email, String timezone) {
    public static Account from(User u) {
      return new Account(u.id(), u.email(), u.timezone());
    }
  }

  public record Tokens(String accessToken, String tokenType, long expiresIn, String refreshToken) {
    @Override
    public String toString() {
      return "Tokens[REDACTED]";
    }
  }
}
