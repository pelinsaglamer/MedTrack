package com.medtrack.common;

import com.medtrack.auth.TokenService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class OperationRegistry {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public OperationRegistry(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // Caller owns a transaction. Unique insert serializes identical operation IDs before medication
  // locks.
  public <T> T replay(
      UUID id, UUID owner, UUID medicationId, Object request, Instant now, Class<T> resultType) {
    String hash = TokenService.hash(json.writeValueAsString(request));
    jdbc.update(
        "insert into operation_registry(id,user_id,medication_id,request_hash,created_at) values (?,?,?,?,?) on conflict do nothing",
        id,
        owner,
        medicationId,
        hash,
        Timestamp.from(now));
    return jdbc.queryForObject(
        "select user_id,medication_id,request_hash,response_json from operation_registry where id=? for update",
        (rs, n) -> {
          if (!owner.equals(rs.getObject("user_id", UUID.class))
              || !medicationId.equals(rs.getObject("medication_id", UUID.class))
              || !hash.equals(rs.getString("request_hash")))
            throw new ApiException(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                "Operation ID was already used for different content");
          String response = rs.getString("response_json");
          return response == null ? null : json.readValue(response, resultType);
        },
        id);
  }

  public void complete(UUID id, Object response) {
    jdbc.update(
        "update operation_registry set response_json=? where id=? and response_json is null",
        json.writeValueAsString(response),
        id);
  }

  public void requireMatching(UUID header, UUID body) {
    if (!header.equals(body))
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VALIDATION_ERROR",
          "Idempotency-Key must equal clientOperationId");
  }
}
