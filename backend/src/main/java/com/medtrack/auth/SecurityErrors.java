package com.medtrack.auth;

import com.medtrack.common.ApiError;
import com.medtrack.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrors {
  private final ObjectMapper json;

  public SecurityErrors(ObjectMapper json) {
    this.json = json;
  }

  public void write(
      HttpServletRequest request, HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    json.writeValue(
        response.getOutputStream(),
        new ApiError(
            code,
            "Request not authorized",
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE),
            Map.of()));
  }
}
