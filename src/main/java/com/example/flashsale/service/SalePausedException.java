package com.example.flashsale.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when a booking arrives during a reconciliation pause. 503 tells the client to retry later.
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class SalePausedException extends RuntimeException {

    public SalePausedException() {
        super("Sale is paused for reconciliation. Retry shortly.");
    }
}
