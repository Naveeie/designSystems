package com.uniphore.uassist.summary.dao.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document("SummaryEventLog")
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
@Data
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummaryUpdateEventLog {

  @Id
  private String id;
  @Indexed
  private String conversationId;
  @Indexed
  private String eventType;
  private List<EventData> eventData;
  private String summary;
  private long eventTime;
  private String tenantId;
  private String environment;
  private long eventProcessedTime;
  private long latency;
  private String businessProcessId;


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }


  @AllArgsConstructor
  @NoArgsConstructor
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  @Data
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class EventData {
    private String value;
    private String eventId;
    private String instanceId;
    private List<String> effectedIntents;
    private boolean headerEffected;
    private String detectedBy;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }

}
