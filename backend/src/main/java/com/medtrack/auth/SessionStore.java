package com.medtrack.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionStore {
  private final JdbcTemplate jdbc;

  public SessionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Session(UUID id, UUID userId, Instant expiresAt, boolean revoked) {}

  public void create(UUID id, UUID userId, Instant now, Instant expires) {
    jdbc.update(
        "insert into auth_sessions(id,user_id,created_at,expires_at) values (?,?,?,?)",
        id,
        userId,
        Timestamp.from(now),
        Timestamp.from(expires));
  }

  public void addToken(String hash, UUID sessionId, Instant now) {
    jdbc.update(
        "insert into refresh_tokens(token_hash,session_id,created_at) values (?,?,?)",
        hash,
        sessionId,
        Timestamp.from(now));
  }

  // Lock the family, not just a token: rotation, logout and replay revocation serialize together.
  public Session lock(String hash) {
    List<Session> rows =
        jdbc.query(
            "select s.id,s.user_id,s.expires_at,s.revoked_at from auth_sessions s join refresh_tokens t on t.session_id=s.id where t.token_hash=? for update of s",
            (rs, n) ->
                new Session(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("revoked_at") != null),
            hash);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public boolean consumed(String hash) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select consumed_at is not null from refresh_tokens where token_hash=?",
            Boolean.class,
            hash));
  }

  public void consume(String hash, Instant now) {
    jdbc.update(
        "update refresh_tokens set consumed_at=? where token_hash=?", Timestamp.from(now), hash);
  }

  public void revoke(UUID id, Instant now) {
    jdbc.update(
        "update auth_sessions set revoked_at=coalesce(revoked_at,?) where id=?",
        Timestamp.from(now),
        id);
  }

  public boolean active(UUID id, UUID owner, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from auth_sessions where id=? and user_id=? and revoked_at is null and expires_at>?)",
            Boolean.class,
            id,
            owner,
            Timestamp.from(now)));
  }
}
