package com.uniphore.uassist.summary.execption;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GenAiExceptionTest {

    @Test
    @Order(1)
    void genAiException_CreatesExceptionWithMessage() {
        String message = "Test message";
        GenAiException exception = new GenAiException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    @Order(2)
    void genAiException_CreatesExceptionWithMessageAndCause() {
        String message = "Test message";
        Throwable cause = new RuntimeException("Cause");
        GenAiException exception = new GenAiException(message, cause);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
