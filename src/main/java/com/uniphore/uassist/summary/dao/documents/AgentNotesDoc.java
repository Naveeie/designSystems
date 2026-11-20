package com.uniphore.uassist.summary.dao.documents;

import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document("AgentNotes")
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = Include.NON_NULL)
public class AgentNotesDoc {
  // HTML Version text
  private String text;
  // Parsed HTML text to plain text
  private String plainText;
  private TimeStampDoc timestamp;

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
