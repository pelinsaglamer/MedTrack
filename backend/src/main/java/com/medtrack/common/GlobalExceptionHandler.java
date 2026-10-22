package com.medtrack.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    // Do not return rejected values or validator messages that might interpolate them.
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> fields.putIfAbsent(e.getField(), "Invalid value"));
    return new ResponseEntity<>(
        error("VALIDATION_ERROR", "Request validation failed", request, fields),
        headers,
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String code =
        switch (status.value()) {
          case 404 -> "NOT_FOUND";
          case 405 -> "METHOD_NOT_ALLOWED";
          case 415 -> "UNSUPPORTED_MEDIA_TYPE";
          default -> "INVALID_REQUEST";
        };
    return new ResponseEntity<>(
        error(code, "Request could not be processed", request, Map.of()), headers, status);
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> api(ApiException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.status())
        .body(
            new ApiError(
                ex.code(),
                ex.getMessage(),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE),
                ex.fields()));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> conflict(HttpServletRequest request) {
    return ResponseEntity.status(409)
        .body(
            new ApiError(
                "RESOURCE_CONFLICT",
                "Resource already exists or conflicts",
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE),
                Map.of()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
    // No exception message, stack trace, URL, body or medication data in logs.
    LOG.error(
        "Unhandled server error; requestId={}", request.getAttribute(RequestIdFilter.ATTRIBUTE));
    return ResponseEntity.internalServerError()
        .body(
            new ApiError(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE),
                Map.of()));
  }

  private ApiError error(
      String code, String message, WebRequest request, Map<String, String> fields) {
    HttpServletRequest servlet = ((ServletWebRequest) request).getRequest();
    return new ApiError(
        code, message, (String) servlet.getAttribute(RequestIdFilter.ATTRIBUTE), fields);
  }
}
