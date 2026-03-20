package zasyaSolutions.mySpaCoverSkuRecommendation.exception;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "failed", exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, "not_found", exception.getMessage());
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchFile(NoSuchFileException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, "not_found", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException exception) {
        return buildResponse(HttpStatus.CONFLICT, "conflict", exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "failed", "Uploaded file exceeds the configured size limit");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipartException(MultipartException exception) {
        log.error("Multipart request failed", exception);
        return buildResponse(HttpStatus.BAD_REQUEST, "failed", "Invalid multipart upload request");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {
        log.error("Execution failed", exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "failed", exception.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException exception) {
        log.error("I/O request failed", exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "failed", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception", exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "error", "Internal server error");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String state, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", state);
        response.put("error", message);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(response);
    }
}
