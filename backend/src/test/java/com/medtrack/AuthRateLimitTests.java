package com.medtrack;

import static org.junit.jupiter.api.Assertions.*;

import com.medtrack.auth.*;
import com.medtrack.common.RequestIdFilter;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.ObjectMapper;

class AuthRateLimitTests {
  @Test
  void limitsBeforeControllerAndResetsAtNextMinute() throws Exception {
    var properties =
        new AuthProperties(
            "test-only-0000000000000000000000000000",
            "issuer",
            "audience",
            Duration.ofMinutes(10),
            Duration.ofDays(30),
            2);
    var clock = new MutableClock();
    var filter = new AuthRateLimitFilter(properties, clock, new SecurityErrors(new ObjectMapper()));
    for (int i = 0; i < 3; i++) {
      var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
      request.setServletPath("/api/v1/auth/login");
      request.setRemoteAddr("127.0.0.1");
      request.setAttribute(RequestIdFilter.ATTRIBUTE, "fixture-id");
      var response = new MockHttpServletResponse();
      filter.doFilter(
          request,
          response,
          (q, s) -> ((jakarta.servlet.http.HttpServletResponse) s).setStatus(204));
      assertEquals(i < 2 ? 204 : 429, response.getStatus());
      if (i == 2) {
        assertTrue(response.getContentAsString().contains("RATE_LIMITED"));
        assertNotNull(response.getHeader("Retry-After"));
      }
    }
    clock.now = clock.now.plusSeconds(60);
    var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setServletPath("/api/v1/auth/login");
    request.setRemoteAddr("127.0.0.1");
    var response = new MockHttpServletResponse();
    filter.doFilter(
        request, response, (q, s) -> ((jakarta.servlet.http.HttpServletResponse) s).setStatus(204));
    assertEquals(204, response.getStatus());
  }

  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-22T10:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
