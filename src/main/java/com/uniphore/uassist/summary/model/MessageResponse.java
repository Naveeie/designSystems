package com.uniphore.uassist.summary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class MessageResponse implements Serializable {

  private static final long serialVersionUID = -1248403801580259881L;

  private String message;

  private String id;

  public MessageResponse(final String message, final String id) {
    super();
    this.message = message;
    this.id = id;
  }

}
