package com.smartops.api.error;

import com.smartops.api.config.CorrelationIdFilter;
import com.smartops.application.port.out.BatchRegistry.DuplicateFileException;
import com.smartops.application.transaction.IdempotencyConflictException;
import com.smartops.application.transaction.TransactionNotFoundException;
import com.smartops.domain.shared.DomainException;
import com.smartops.domain.transaction.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Manejo centralizado de errores en formato RFC 7807 (application/problem+json).
 * Cada respuesta incluye un "code" estable y el correlationId para soporte.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail invalidTransition(InvalidStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail transactionNotFound(TransactionNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(DuplicateFileException.class)
    ProblemDetail duplicateFile(DuplicateFileException e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, "DUPLICATE_FILE", e.getMessage());
        p.setProperty("existingFileId", e.getExistingFileId());
        return p;
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Error no controlado", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Error interno. Reporte el correlationId a soporte.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La solicitud tiene campos inválidos");
        p.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<String> errors = ex.getAllErrors().stream().map(e -> String.valueOf(e.getDefaultMessage())).toList();
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parámetros inválidos");
        p.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        if (body instanceof ProblemDetail p) {
            p.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        p.setProperty("code", code);
        p.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return p;
    }
}
