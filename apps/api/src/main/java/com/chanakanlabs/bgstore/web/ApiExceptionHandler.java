package com.chanakanlabs.bgstore.web;

import com.chanakanlabs.bgstore.contract.model.FieldError;
import com.chanakanlabs.bgstore.contract.model.ProblemDetail;
import com.chanakanlabs.bgstore.contract.model.ValidationProblem;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates the failures the API raises into the {@code ProblemDetail} and {@code
 * ValidationProblem} shapes the contract publishes.
 *
 * <p>Everything else keeps Spring's own RFC 9457 responses, enabled through {@code
 * spring.mvc.problemdetails.enabled}.
 */
@RestControllerAdvice
// Spring registers its own problem-detail advice at order 0, so this one has to
// come first to answer a rejected body with 422 instead of its default 400.
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

  /** Bean-validation codes that mean "you left this out" rather than "this value is wrong". */
  private static final Set<String> MISSING_VALUE_CODES = Set.of("NotNull", "NotBlank", "NotEmpty");

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException exception) {
    var problem = new ProblemDetail("about:blank", "Not Found", HttpStatus.NOT_FOUND.value());
    problem.setDetail(exception.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(ValidationFailedException.class)
  ResponseEntity<ValidationProblem> handleValidationFailed(ValidationFailedException exception) {
    return unprocessable(
        exception.violations().stream()
            .map(violation -> new FieldError(violation.field(), violation.message()))
            .toList());
  }

  /**
   * Schema-level rejections from {@code @Valid @RequestBody}. Returned as 422 rather than Spring's
   * default 400 so a browser form can tell "this payload broke a rule" from "this payload was not
   * understood" without inspecting the body.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ValidationProblem> handleInvalidBody(MethodArgumentNotValidException exception) {
    return unprocessable(
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), messageFor(error.getCode())))
            .distinct()
            .toList());
  }

  /**
   * A value that could not even be converted to its declared type: an unknown enum value, or an id
   * that is not a UUID. A path that cannot name a record is answered as a missing record; a query
   * parameter is answered like any other rejected value.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<?> handleUnconvertibleValue(MethodArgumentTypeMismatchException exception) {
    var parameter = exception.getParameter();
    if (parameter.hasParameterAnnotation(PathVariable.class)) {
      var rejected = exception.getValue();
      return handleNotFound(
          new ResourceNotFoundException("Resource", rejected == null ? "" : rejected));
    }

    return unprocessable(List.of(new FieldError(exception.getName(), FieldViolation.INVALID)));
  }

  private static ResponseEntity<ValidationProblem> unprocessable(List<FieldError> errors) {
    var problem =
        new ValidationProblem(
            "about:blank",
            "Unprocessable Content",
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            errors);
    problem.setDetail("The request violates " + errors.size() + " rule(s).");

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * {@code code} is the bean-validation annotation name, or null for a non-annotation rejection.
   */
  private static String messageFor(@Nullable String code) {
    return code != null && MISSING_VALUE_CODES.contains(code)
        ? FieldViolation.REQUIRED
        : FieldViolation.INVALID;
  }
}
