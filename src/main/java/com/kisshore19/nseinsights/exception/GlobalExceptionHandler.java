package com.kisshore19.nseinsights.exception;

import com.kisshore19.nseinsights.dto.response.ApiResponse;
import com.kisshore19.nseinsights.dto.response.DownloadStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDate(InvalidDateException ex) {
        log.warn("Invalid date: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_DATE", ex.getMessage()));
    }

    @ExceptionHandler(DataAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<DownloadStatusResponse>> handleAlreadyExists(DataAlreadyExistsException ex) {
        var data = DownloadStatusResponse.builder()
                .tradeDate(ex.getTradeDate())
                .downloaded(true)
                .recordCount(ex.getExistingRecords())
                .build();
        return ResponseEntity.ok(ApiResponse.alreadyExists(
                "Data for " + ex.getTradeDate() + " already downloaded. Pass overwrite=true to re-download.",
                data));
    }

    @ExceptionHandler(DateNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(DateNotFoundException ex) {
        log.warn("Date not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("DATE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(NseUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNseUnavailable(NseUnavailableException ex) {
        log.error("NSE unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("NSE_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
    }
}
