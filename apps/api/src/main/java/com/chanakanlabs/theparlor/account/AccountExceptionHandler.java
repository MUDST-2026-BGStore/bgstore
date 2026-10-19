package com.chanakanlabs.theparlor.account;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class AccountExceptionHandler {
  @ExceptionHandler(AccountNotProvisionedException.class)
  ProblemDetail handleAccountNotProvisioned(AccountNotProvisionedException ex) {

    return ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND, "Account has not been provisioned.");
  }
}
