package com.medtrack.account;

import com.medtrack.auth.AuthDtos;
import com.medtrack.auth.UserRepository;
import com.medtrack.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final CurrentUser current;
  private final UserRepository users;

  public AccountService(CurrentUser current, UserRepository users) {
    this.current = current;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public AuthDtos.Account me() {
    // Owner comes exclusively from the verified principal, never a request parameter.
    return AuthDtos.Account.from(
        users
            .findById(current.id())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required")));
  }
}
