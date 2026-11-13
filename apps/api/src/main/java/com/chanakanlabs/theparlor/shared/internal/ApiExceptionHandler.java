package com.chanakanlabs.theparlor.shared.internal;

import com.chanakanlabs.theparlor.contracts.model.Problem;
import com.chanakanlabs.theparlor.shared.ProblemException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maps application failures to the contract's Problem body for every controller. Module advice with
 * its own error codes must be ordered ahead of this one: the first advice with a matching handler
 * wins, regardless of how narrowly it is scoped.
 *
 * <p>There is deliberately no catch-all handler. Authorization failures must reach Spring
 * Security's filter chain, and unexpected errors must stay 500s handled by Spring Boot.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Problem> invalidInput(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(new Problem(400, "INVALID_REQUEST", exception.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    HttpMessageNotReadableException.class,
    jakarta.validation.ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Problem> invalidContract(Exception exception) {
    return ResponseEntity.badRequest()
        .body(new Problem(400, "INVALID_REQUEST", "Request does not match the API contract"));
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ObjectOptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<Problem> conflictingWrite(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new Problem(
                409,
                "CONFLICT",
                "The change conflicts with existing or concurrently updated data"));
  }

  @ExceptionHandler(ProblemException.class)
  ResponseEntity<Problem> codedFailure(ProblemException exception) {
    return ResponseEntity.status(exception.status())
        .body(new Problem(exception.status().value(), exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Problem> applicationFailure(ResponseStatusException exception) {
    int status = exception.getStatusCode().value();
    String code =
        switch (status) {
          case 400 -> "INVALID_REQUEST";
          case 403 -> "FORBIDDEN";
          case 404 -> "NOT_FOUND";
          case 409 -> "CONFLICT";
          default -> status >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    return ResponseEntity.status(exception.getStatusCode())
        .body(new Problem(status, code, exception.getReason()));
  }
}
