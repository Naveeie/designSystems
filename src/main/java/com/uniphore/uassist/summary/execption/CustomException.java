package com.uniphore.uassist.summary.execption;

import org.springframework.http.HttpStatus;
import lombok.ToString;

@lombok.Setter
@lombok.Getter
@ToString
public class CustomException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private String code;
  private HttpStatus statusCode;
  
  public CustomException(final String code, final HttpStatus statusCode) {
    super();
    this.code = code;
    this.statusCode = statusCode;
  }

  public CustomException(final String code, final HttpStatus statusCode, final Throwable cause) {
    super(cause);
    this.code = code;
    this.statusCode = statusCode;
  }

}
