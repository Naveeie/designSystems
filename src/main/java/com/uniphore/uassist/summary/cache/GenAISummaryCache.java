package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Builder(builderClassName = "Builder", toBuilder = true)
public class GenAISummaryCache {

  @JsonProperty("isOnDemandSummaryEnabled")
  private boolean isOnDemandSummaryEnabled;
  @JsonProperty("isAcwSummaryEnabled")
  private boolean acwSummaryEnabled;
  @JsonProperty("isFormatEnabled")
  private boolean formatEnabled;
  private SummaryFormat summaryFormat;
  private SummaryOptIn summaryOptIn;
  private boolean showUndSpkEntity;
  private String targetLanguage;
  private Map<String, List<SpokenEntity>> sectionMap;
  private boolean shortCallEnabled;
  private ShortCallConfig shortCallConfig;
  private List<CallTypeTemplateMapping> callTypeTemplateMappings;


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  public static class SummaryFormat {
    @JsonProperty("call-reason")
    private String callReason;
    @JsonProperty("agent-steps")
    private String agentSteps;
    private String outcome;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  public static class ShortCallConfig {
    private String message;
    private Integer duration;
    private String durationType;
    private Turns turns;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @lombok.Builder(toBuilder = true)
  public static class Turns {
    private String channel;
    private int threshold;
  }


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  public static class SummaryOptIn {
    @JsonProperty("isCallReasonEnabled")
    private boolean isCallReasonEnabled;
    @JsonProperty("isAgentStepsEnabled")
    private boolean isAgentStepsEnabled;
    @JsonProperty("isOutcomeEnabled")
    private boolean isOutcomeEnabled;
    @JsonProperty("isCustomerSatisfactionEnabled")
    private boolean isCustomerSatisfactionEnabled;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  public static class SpokenEntity {
    private String entityId;
    private String variableId;
    private String name;
    private String label;
    private String placement;
    @JsonProperty("isShowLabel")
    private boolean showLabel;
    @JsonProperty("isIncludeLabel")
    private boolean includeLabel;
    private String type;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  public static class CallTypeTemplateMapping {
    private String intentLabel;
    private String templateId;
    private String templateVId;
    private Integer version;


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }
  }


  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
  }

}
