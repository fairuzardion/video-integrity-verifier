package com.noexperience.integrity.api;

import com.noexperience.integrity.common.CryptoOperationException;
import com.noexperience.integrity.common.IntegrityException;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.common.MediaProcessingException;
import com.noexperience.integrity.common.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Maps every failure to a consistent {@link ApiError} body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiError> invalidInput(InvalidInputException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(MediaProcessingException.class)
    public ResponseEntity<ApiError> media(MediaProcessingException e, HttpServletRequest request) {
        log.warn("Media processing failed: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), request);
    }

    @ExceptionHandler(CryptoOperationException.class)
    public ResponseEntity<ApiError> crypto(CryptoOperationException e, HttpServletRequest request) {
        log.error("Cryptographic failure", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), request);
    }

    @ExceptionHandler(IntegrityException.class)
    public ResponseEntity<ApiError> domain(IntegrityException e, HttpServletRequest request) {
        log.error("Integrity service failure", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the configured size limit (MAX_UPLOAD_SIZE)", request);
    }

    /** Spring MVC's own exceptions (missing part, wrong media type, 405 ...) keep their natural status. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> fallback(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null) {
                String detail = errorResponse.getBody().getDetail();
                return build(status, detail != null ? detail : status.getReasonPhrase(), request);
            }
        }
        log.error("Unexpected error on {}", request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal error", request);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now().toString(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
