package com.uniphore.uassist.summary.execption;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Setter
@lombok.Getter
@lombok.Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse implements Serializable {

    /**
     * Serialization version ID for ReturnMessagesAbapRestOutput
     */
    private static final long serialVersionUID = 7139587356985460361L;

    private String code;

    private String message;

    public ErrorResponse(final String code, final String message) {
        super();
        this.code = code;
        this.message = message;
    }
    
}
