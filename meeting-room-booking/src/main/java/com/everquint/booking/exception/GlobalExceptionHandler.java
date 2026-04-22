package com.everquint.booking.exception;

import com.everquint.booking.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppExceptions.ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(AppExceptions.ValidationException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("ValidationError", ex.getMessage()));
    }

    @ExceptionHandler(AppExceptions.NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AppExceptions.NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NotFoundError", ex.getMessage()));
    }

    @ExceptionHandler(AppExceptions.ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(AppExceptions.ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ConflictError", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("ValidationError", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("InternalError", ex.getMessage()));
    }
}
