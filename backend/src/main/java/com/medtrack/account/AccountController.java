package com.medtrack.account;

import com.medtrack.auth.AuthDtos;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
  private final AccountService accounts;

  public AccountController(AccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/me")
  public AuthDtos.Account me() {
    return accounts.me();
  }
}
