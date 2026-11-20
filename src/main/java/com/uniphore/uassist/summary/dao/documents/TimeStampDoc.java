package com.uniphore.uassist.summary.dao.documents;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document("TimeStamp")
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = Include.NON_NULL)
public class TimeStampDoc {

  @Indexed
  private long seconds;
  private int nanos;
  
  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
