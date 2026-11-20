package com.uniphore.uassist.summary.execption;

import com.uniphore.uassist.summary.common.Commons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ExceptionAdviceHandler {

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
      final CustomException ex) {

    log.debug("Error code: {}", ex.getCode());
    final ErrorResponse errorResponse = new ErrorResponse(ex.getCode(), ErrorCodes.getMessage(ex.getCode()));
    return Commons.getErrorResponse(errorResponse, ex.getStatusCode());
  }
  
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
      List<ErrorResponse> errors = new ArrayList<>();
      ex.getBindingResult().getAllErrors().forEach((error) -> {
          String errorCode = error.getDefaultMessage();
          errors.add(new ErrorResponse(errorCode,  ErrorCodes.getMessage(errorCode)));
      });
      return Commons.getErrorResponse(errors, HttpStatus.BAD_REQUEST);
  }
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
      log.debug("Constraint violation exception encountered: {}", ex.getConstraintViolations(), ex);
      return Commons.getErrorResponse(buildValidationErrors(ex.getConstraintViolations()), HttpStatus.BAD_REQUEST);
  }

  private List<ErrorResponse> buildValidationErrors(Set<ConstraintViolation<?>> violations) {
      return violations.
              stream().
              map(violation ->
                      ErrorResponse.builder().code(violation.getMessage()).
                                message(ErrorCodes.getMessage(violation.getMessage())).
                                build()).
              collect(toList());
    }

}
