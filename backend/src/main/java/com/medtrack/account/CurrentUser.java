package com.medtrack.account;

import com.medtrack.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  // Future repositories must query by resource ID AND this trusted owner ID.
  public UUID id() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    return UUID.fromString(jwt.getSubject());
  }

  public void requireOwnership(UUID owner) {
    if (!id().equals(owner))
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }
}
