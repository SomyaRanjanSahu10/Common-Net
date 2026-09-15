package com.mailflow.exception;

import org.springframework.http.HttpStatus;

/** Generic checked-status exception, thrown by services and turned into a
 *  JSON { message } body by GlobalExceptionHandler — replaces the ad-hoc
 *  res.status(xxx).json({ message }) calls scattered through the Express routes. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
