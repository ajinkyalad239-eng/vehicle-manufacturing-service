package com.example.manufacturing.common.exception;

/** Thrown when a unique business key (plant code, model code, ...) already exists. Mapped to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
