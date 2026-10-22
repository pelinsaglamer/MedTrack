package com.medtrack.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthDtos.Account register(@Valid @RequestBody AuthDtos.Register input) {
    return auth.register(input);
  }

  @PostMapping("/login")
  public AuthDtos.Tokens login(@Valid @RequestBody AuthDtos.Login input) {
    return auth.login(input);
  }

  @PostMapping("/refresh")
  public AuthDtos.Tokens refresh(@Valid @RequestBody AuthDtos.Refresh input) {
    return auth.refresh(input);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody AuthDtos.Refresh input) {
    auth.logout(input);
  }
}
