package com.stockflow.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception, HttpServletRequest request) {
        var problem = problem(exception.status(), exception.type(), exception.code(), exception.getMessage(), request);
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                URI.create("https://stockflow.local/problems/validation-failed"),
                "VALIDATION_FAILED",
                "Request validation failed",
                request);
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintValidation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<Map<String, String>> violations = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList();
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                URI.create("https://stockflow.local/problems/validation-failed"),
                "VALIDATION_FAILED",
                "Request validation failed",
                request);
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                URI.create("https://stockflow.local/problems/validation-failed"),
                "VALIDATION_FAILED",
                "Invalid value for query parameter " + exception.getName(),
                request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.CONFLICT,
                URI.create("https://stockflow.local/problems/data-conflict"),
                "DATA_CONFLICT",
                "The request conflicts with existing data",
                request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.CONFLICT,
                URI.create("https://stockflow.local/problems/concurrent-modification"),
                "CONCURRENT_MODIFICATION",
                "The resource changed while this request was being processed; retry the request",
                request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private ProblemDetail problem(
            HttpStatus status, URI type, String code, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
