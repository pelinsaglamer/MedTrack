package com.medtrack.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

// Single-process limiter; direct peer only, never an untrusted forwarded IP header.
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private record Window(long minute, int count) {}

  private final Map<String, Window> windows = new HashMap<>();
  private final AuthProperties properties;
  private final Clock clock;
  private final SecurityErrors errors;

  public AuthRateLimitFilter(AuthProperties properties, Clock clock, SecurityErrors errors) {
    this.properties = properties;
    this.clock = clock;
    this.errors = errors;
  }

  private synchronized boolean allowed(String peer) {
    long minute = clock.instant().getEpochSecond() / 60;
    windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
    Window old = windows.get(peer);
    if (old == null && windows.size() >= 10000) return false;
    int count = old == null ? 0 : old.count();
    if (count >= properties.rateLimit()) return false;
    windows.put(peer, new Window(minute, count + 1));
    return true;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getMethod().equals("POST")
        && request.getServletPath().startsWith("/api/v1/auth/")) {
      response.setHeader("Cache-Control", "no-store");
      if (!allowed(request.getRemoteAddr())) {
        response.setHeader(
            "Retry-After", Long.toString(60 - clock.instant().getEpochSecond() % 60));
        errors.write(request, response, 429, "RATE_LIMITED");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
