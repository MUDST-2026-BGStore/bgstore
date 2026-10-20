package com.chanakanlabs.theparlor.catalog.internal.web;

import com.chanakanlabs.theparlor.contracts.model.Problem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "com.chanakanlabs.theparlor.catalog.internal.web")
class CatalogExceptionHandler {
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
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Problem> invalidContract(Exception exception) {
    return ResponseEntity.badRequest()
        .body(new Problem(400, "INVALID_REQUEST", "Request does not match the API contract"));
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ObjectOptimisticLockingFailureException.class
  })
  ResponseEntity<Problem> conflictingWrite(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new Problem(
                409,
                "CATALOG_CONFLICT",
                "The catalog entry conflicts with existing or concurrently updated data"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Problem> applicationFailure(ResponseStatusException exception) {
    int status = exception.getStatusCode().value();
    String code =
        status == 404 ? "NOT_FOUND" : status == 409 ? "CATALOG_CONFLICT" : "INVALID_REQUEST";
    return ResponseEntity.status(exception.getStatusCode())
        .body(new Problem(status, code, exception.getReason()));
  }
}
