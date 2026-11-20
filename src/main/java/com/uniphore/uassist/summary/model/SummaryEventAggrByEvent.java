package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;

import java.util.List;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@JsonDeserialize(builder = SummaryEventAggrByEvent.Builder.class)
@lombok.Builder(builderClassName = "Builder", toBuilder = true)
public class SummaryEventAggrByEvent {
  private String event;
  private List<SummaryUpdateEventLog> eventData;


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }
}
