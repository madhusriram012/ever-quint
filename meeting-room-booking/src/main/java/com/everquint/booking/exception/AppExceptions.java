package com.everquint.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class AppExceptions {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
