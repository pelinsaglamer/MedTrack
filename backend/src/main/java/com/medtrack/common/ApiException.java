package com.medtrack.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final Map<String, String> fields;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = Map.copyOf(fields);
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Map<String, String> fields() {
    return fields;
  }
}
