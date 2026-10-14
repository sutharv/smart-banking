package com.smartbanking.exception;

import com.smartbanking.dto.TransferDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every error leaves the API as RFC 9457 problem details with a stable machine-readable
 * {@code code}. Unexpected failures never expose stack traces or internal messages; they get an
 * {@code errorId} that matches the server log line.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(problem(ex.getStatus(), ex.getCode(), ex.getMessage(), request));
    }

    @ExceptionHandler(TransferRejectedException.class)
    ResponseEntity<ProblemDetail> handleRejected(TransferRejectedException ex, HttpServletRequest request) {
        TransferDto transfer = ex.getTransfer();
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, transfer.rejectionReason().name(),
                rejectionMessage(transfer), request);
        problem.setProperty("transferId", transfer.id());
        return respond(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The request contains invalid fields.", request);
        problem.setProperty("errors", errors);
        return respond(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleInvalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request contains invalid parameters.", request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is missing or is not valid JSON for this endpoint.", request));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> handleBadParameter(Exception ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "A path, query or header parameter is missing or has an invalid value.", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this operation.", request));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleLockFailure(RuntimeException ex, HttpServletRequest request) {
        log.warn("Concurrent update conflict on {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return respond(problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "The resource was changed by another request. Please retry.", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Most commonly two simultaneous requests with the same Idempotency-Key.
        log.warn("Data integrity conflict on {}", request.getRequestURI());
        return respond(problem(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data, possibly a duplicate request in progress. Please retry.", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint exists at this path.", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This HTTP method is not supported for this endpoint.", request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Send the request body as application/json.", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unexpected error {} on {} {}", errorId, request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. No changes were saved.", request);
        problem.setProperty("errorId", errorId);
        return respond(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(),
                "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
    }

    private static String rejectionMessage(TransferDto transfer) {
        return switch (transfer.rejectionReason()) {
            case INSUFFICIENT_FUNDS -> "The source account does not have enough funds for this transfer.";
            case SOURCE_ACCOUNT_INACTIVE -> "The source account is not active.";
            case DESTINATION_ACCOUNT_INACTIVE -> "The destination account is not active.";
            case CURRENCY_MISMATCH -> "Both accounts must use the transfer currency; currency conversion is not supported.";
        };
    }
}
